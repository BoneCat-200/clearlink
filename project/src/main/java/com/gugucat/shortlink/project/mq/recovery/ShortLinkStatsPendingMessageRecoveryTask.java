package com.gugucat.shortlink.project.mq.recovery;

import com.gugucat.shortlink.project.mq.consumer.ShortLinkStatsSaveConsumer;
import com.gugucat.shortlink.project.mq.handler.ShortLinkStatsMessageHandler;
import com.gugucat.shortlink.project.mq.handler.ShortLinkStatsMessageHandler.HandleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_DLQ_KEY;
import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * 统计消息的「待确认消息」回收任务
 * <p>
 * Redis Stream 的消费组有一个待确认列表（PEL）：消息被投递后如果没有 ACK，
 * 就会停在那里。消费者因为异常没 ACK 时，消息就卡在 PEL 里 ——
 * 容器只会读**新**消息（`ReadOffset.lastConsumed()`），不会回头再读 PEL。
 * <p>
 * 所以必须有这个任务：定期扫描 PEL，把「放了足够久」的消息重新认领（XCLAIM）
 * 给消费者再处理一遍；重试次数超限的转入死信 Stream，避免同一条坏消息无限循环。
 * <p>
 * 阈值的选择：
 * <ul>
 *   <li>{@code MIN_IDLE = 30s}：比正常处理耗时（秒级）长得多，不会误抢正在处理的消息</li>
 *   <li>{@code MAX_DELIVERY_COUNT = 5}：首次投递 + 4 次重试。
 *       幂等键的 TTL 是 2 分钟，5 轮 × 30 秒 = 2.5 分钟，
 *       足够让「进程崩溃留下的处理中标记」过期，不会把可恢复的消息误判成死信</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "redis-stream", matchIfMissing = true)
public class ShortLinkStatsPendingMessageRecoveryTask {

    private final ShortLinkStatsMessageHandler shortLinkStatsMessageHandler;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 重试计数键，%s 为消息 ID
     */
    private static final String RETRY_COUNT_KEY = "clearlink:stats-stream:retry:%s";

    /**
     * 重试计数的过期时间
     */
    private static final Duration RETRY_COUNT_TTL = Duration.ofHours(1);

    /**
     * 消息闲置多久才认为「该重试了」
     */
    private static final Duration MIN_IDLE = Duration.ofSeconds(30);

    /**
     * 最大投递次数，超过则转入死信
     */
    private static final long MAX_DELIVERY_COUNT = 5;

    /**
     * 每轮最多扫描多少条待确认消息
     */
    private static final long SCAN_BATCH_SIZE = 100;

    /**
     * 定期回收
     * <p>
     * 启动后延迟 60 秒才开始：避开应用启动期的连接与初始化，
     * 也避免把启动瞬间还没处理完的消息当成「闲置」。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void recover() {
        StreamOperations<String, String, String> streamOperations = stringRedisTemplate.opsForStream();
        PendingMessages pendingMessages;
        try {
            pendingMessages = streamOperations.pending(
                    SHORT_LINK_STATS_STREAM_TOPIC_KEY,
                    SHORT_LINK_STATS_STREAM_GROUP_KEY,
                    Range.unbounded(),
                    SCAN_BATCH_SIZE);
        } catch (Throwable ex) {
            // 回收任务本身失败不应该影响主流程，等下一轮
            log.error("读取待确认统计消息失败", ex);
            return;
        }
        if (pendingMessages == null) {
            return;
        }
        for (PendingMessage pendingMessage : pendingMessages) {
            try {
                recoverOne(streamOperations, pendingMessage);
            } catch (Throwable ex) {
                log.error("回收待确认统计消息失败，messageId: {}", pendingMessage.getIdAsString(), ex);
            }
        }
    }

    /**
     * 处理一条待确认消息
     * <p>
     * <b>没有用 XCLAIM 认领</b>：本仓库的 Redis 客户端是 Redisson（spring-data-redis 的
     * `RedissonStreamCommands.xClaim` 在 3.21.3 上会抛 NPE）。更重要的是**并不需要**认领：
     * 认领的作用是转移所有权并把「闲置计时」重置，而这里的目标只是「取出消息体再处理一次」。
     * <p>
     * 因此改为：
     * <ol>
     *   <li>用 XRANGE 按消息 ID 取出消息体（仍在 Stream 里，失败时没有被删除）</li>
     *   <li>用一个 Redis 计数器自己记重试次数（XPENDING 的 delivery-count 因为没认领不会增长）</li>
     *   <li>处理成功就 ACK + 删除 + 清计数；失败就留在待确认列表，下一轮再来</li>
     * </ol>
     *
     * @param streamOperations Stream 操作入口
     * @param pendingMessage   待确认消息的元信息
     */
    private void recoverOne(StreamOperations<String, String, String> streamOperations, PendingMessage pendingMessage) {
        if (pendingMessage.getElapsedTimeSinceLastDelivery().compareTo(MIN_IDLE) < 0) {
            // 还没闲置够久：可能正被消费者处理，别抢
            return;
        }
        String messageId = pendingMessage.getIdAsString();
        List<MapRecord<String, String, String>> records = streamOperations.range(
                SHORT_LINK_STATS_STREAM_TOPIC_KEY,
                Range.closed(messageId, messageId));
        if (records.isEmpty()) {
            // 消息体已经不在 Stream 里了（被删但没 ACK 的残留待确认项）：清掉即可
            acknowledgeAndDelete(stringRedisTemplate, messageId);
            return;
        }
        MapRecord<String, String, String> record = records.get(0);
        long retryCount = increaseRetryCount(messageId);
        if (retryCount > MAX_DELIVERY_COUNT) {
            moveToDeadLetter(streamOperations, record, retryCount);
            return;
        }
        HandleResult result = shortLinkStatsMessageHandler.handle(
                record.getValue().get("statsRecord"),
                messageId);
        if (result == HandleResult.SUCCESS || result == HandleResult.ALREADY_DONE) {
            acknowledgeAndDelete(stringRedisTemplate, messageId);
            stringRedisTemplate.delete(String.format(RETRY_COUNT_KEY, messageId));
        }
        // FAILED / IN_PROGRESS：留在 PEL 里，下一轮再来（重试次数已 +1）
    }

    /**
     * 重试计数 +1
     * <p>
     * 为什么自己记而不是用 XPENDING 的 delivery-count：不认领的话那个计数不会增长。
     * TTL 取 1 小时 —— 计数只为「这一轮循环」服务，消息最终一定会进死信或被处理掉。
     *
     * @param messageId 消息 ID
     * @return 本次是第几次重试
     */
    private long increaseRetryCount(String messageId) {
        String key = String.format(RETRY_COUNT_KEY, messageId);
        Long retryCount = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, RETRY_COUNT_TTL);
        return retryCount == null ? 1L : retryCount;
    }

    /**
     * 确认并删除一条消息
     *
     * @param stringRedisTemplate Redis 客户端
     * @param messageId           消息 ID
     */
    private void acknowledgeAndDelete(StringRedisTemplate stringRedisTemplate, String messageId) {
        ShortLinkStatsSaveConsumer.acknowledgeAndDelete(
                stringRedisTemplate,
                SHORT_LINK_STATS_STREAM_TOPIC_KEY,
                RecordId.of(messageId));
    }

    /**
     * 转入死信 Stream
     * <p>
     * 保留原始消息体，方便事后排查或重放；另外补上原始消息 ID、投递次数与失败时间。
     *
     * @param streamOperations   Stream 操作入口
     * @param record             待确认的消息
     * @param totalDeliveryCount 已投递次数
     */
    private void moveToDeadLetter(StreamOperations<String, String, String> streamOperations,
                                  MapRecord<String, String, String> record,
                                  long retryCount) {
        Map<String, String> deadLetter = new HashMap<>(record.getValue());
        deadLetter.put("originStream", record.getStream());
        deadLetter.put("originMessageId", record.getId().getValue());
        deadLetter.put("retryCount", String.valueOf(retryCount));
        deadLetter.put("failedAt", String.valueOf(System.currentTimeMillis()));
        streamOperations.add(SHORT_LINK_STATS_STREAM_DLQ_KEY, deadLetter);
        // 从待确认列表与 Stream 里都清掉，否则下一轮会重复转入死信
        acknowledgeAndDelete(stringRedisTemplate, record.getId().getValue());
        stringRedisTemplate.delete(String.format(RETRY_COUNT_KEY, record.getId().getValue()));
        log.error("统计消息重试 {} 次仍未成功，已转入死信队列 {}。messageId: {}",
                retryCount, SHORT_LINK_STATS_STREAM_DLQ_KEY, record.getId().getValue());
    }
}
