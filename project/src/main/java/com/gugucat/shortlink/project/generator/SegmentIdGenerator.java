package com.gugucat.shortlink.project.generator;

import com.gugucat.shortlink.project.config.ShortCodeProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 号段 ID 生成器
 * <p>
 * 采用美团 Leaf 的 segment 模式：一次从数据库领一段连续 ID，在本地内存里逐个发；
 * 剩余量低于阈值时，用后台线程<b>异步</b>预取下一段，避免号段耗尽时建链线程卡在数据库上。
 * <p>
 * 正确性依赖两点：
 * <ol>
 *   <li>{@link SegmentAllocator} 的 UPDATE + SELECT 在同一事务内完成，号段不会重叠；</li>
 *   <li>本地 {@link Segment} 用 {@link AtomicLong} 递增，多线程取号不会拿到同一个值。</li>
 * </ol>
 * 实例宕机时未发完的号段会永久废弃 —— 这是号段模式的正常代价，6 位空间足够大。
 * <p>
 * 说明：预取与同步兜底在极端时序下可能各领一段，导致其中一段被丢弃（日志会记录）。
 * 丢弃的是号段空间而不是唯一性，因此可以接受；不在取号热路径上做更重的协调。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.code.generator", havingValue = "segment", matchIfMissing = true)
public class SegmentIdGenerator {

    private final SegmentAllocator segmentAllocator;

    private final ShortCodeProperties shortCodeProperties;

    /**
     * 预取线程：单线程即可，号段领取是低频操作
     */
    private final ExecutorService prefetchExecutor;

    /**
     * 保护 current / next 切换与预取状态的锁
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 正在发号的号段
     */
    private volatile Segment current;

    /**
     * 已预取、等待接班的号段
     */
    private volatile Segment next;

    /**
     * 是否已有预取任务在执行，避免并发触发多次预取
     */
    private volatile boolean prefetching;

    public SegmentIdGenerator(SegmentAllocator segmentAllocator, ShortCodeProperties shortCodeProperties) {
        this.segmentAllocator = segmentAllocator;
        this.shortCodeProperties = shortCodeProperties;
        this.prefetchExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "segment-prefetch");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 取下一个 ID
     *
     * @return 全局唯一的递增 ID
     */
    public long nextId() {
        for (; ; ) {
            Segment segment = current;
            if (segment != null) {
                long id = segment.nextId();
                if (id != -1) {
                    maybePrefetch(segment);
                    return id;
                }
            }
            // 当前段耗尽（或尚未初始化）：在锁内切换/领取。
            // 切换后重新进入循环，从新段取号
            lock.lock();
            try {
                if (current == segment) {
                    if (next != null) {
                        current = next;
                        next = null;
                    } else {
                        current = allocateSegment();
                        log.info("领取新号段：[{}, {}]", current.start, current.end);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 判断是否需要异步预取下一段
     *
     * @param segment 当前号段
     */
    private void maybePrefetch(Segment segment) {
        double prefetchRatio = shortCodeProperties.getPrefetchRatio();
        if (prefetchRatio <= 0 || prefetchRatio >= 1) {
            return;
        }
        if (segment.remaining() > segment.size() * prefetchRatio) {
            return;
        }
        if (next != null || prefetching) {
            return;
        }
        lock.lock();
        try {
            // 双重检查：同一时刻只允许一个预取任务
            if (next != null || prefetching) {
                return;
            }
            prefetching = true;
        } finally {
            lock.unlock();
        }
        try {
            prefetchExecutor.execute(this::prefetch);
        } catch (RejectedExecutionException ex) {
            // 执行器已关闭（应用停机）或拒绝任务：当前段耗尽时会同步兜底
            lock.lock();
            try {
                prefetching = false;
            } finally {
                lock.unlock();
            }
            log.error("号段预取任务提交失败", ex);
        }
    }

    /**
     * 后台预取下一段
     */
    private void prefetch() {
        try {
            Segment fetched = allocateSegment();
            lock.lock();
            try {
                if (next == null) {
                    next = fetched;
                    log.info("号段预取完成：[{}, {}]", fetched.start, fetched.end);
                } else {
                    // 同步兜底已经领过一段，这段就浪费掉；只记录，不影响正确性
                    log.warn("号段预取结果被丢弃（已有待接班号段）：[{}, {}]", fetched.start, fetched.end);
                }
            } finally {
                lock.unlock();
            }
        } catch (Throwable ex) {
            // 预取失败不影响当前号段；下一次取号会再次触发预取
            log.error("号段预取失败，当前号段耗尽后将同步领取", ex);
        } finally {
            lock.lock();
            try {
                prefetching = false;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 从数据库领取一段
     *
     * @return 号段
     */
    private Segment allocateSegment() {
        SegmentAllocator.SegmentRange range = segmentAllocator.allocate(shortCodeProperties.getBizTag());
        return new Segment(range.getStart(), range.getEnd());
    }

    /**
     * 应用停机时释放预取线程
     */
    @PreDestroy
    public void shutdown() {
        prefetchExecutor.shutdownNow();
    }

    /**
     * 内存中的号段
     */
    private static final class Segment {

        /**
         * 区间起点（含）
         */
        private final long start;

        /**
         * 区间终点（含）
         */
        private final long end;

        /**
         * 下一个待发放的 ID
         */
        private final AtomicLong cursor;

        private Segment(long start, long end) {
            this.start = start;
            this.end = end;
            this.cursor = new AtomicLong(start);
        }

        /**
         * 取号
         *
         * @return 区间内的 ID；耗尽返回 -1
         */
        private long nextId() {
            long id = cursor.getAndIncrement();
            return id > end ? -1 : id;
        }

        /**
         * 剩余可发放数量
         *
         * @return 剩余数量，最小为 0
         */
        private long remaining() {
            return Math.max(0, end - cursor.get() + 1);
        }

        /**
         * 号段总长度
         *
         * @return 长度
         */
        private long size() {
            return end - start + 1;
        }
    }
}
