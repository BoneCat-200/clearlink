package com.gugucat.shortlink.project.generator;

import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.dao.entity.SegmentDO;
import com.gugucat.shortlink.project.dao.mapper.SegmentMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 号段分配器
 * <p>
 * <b>为什么单独一个 Bean</b>：{@link SegmentIdGenerator} 需要在「号段耗尽」时调用它，
 * 如果把它写成生成器自己的私有方法，自调用会绕过 Spring 的事务代理，
 * UPDATE 与 SELECT 就不在同一个事务里 —— 行锁提前释放，并发实例可能拿到重叠号段。
 * 拆成独立 Bean 后，调用一定经过代理。
 */
@Component
@RequiredArgsConstructor
public class SegmentAllocator {

    private final SegmentMapper segmentMapper;

    /**
     * 领取一个号段
     * <p>
     * 事务内先 UPDATE（原子递增并锁行），再 SELECT 读回本次递增后的值。
     * 区间为 {@code (max_id - step, max_id]}。
     *
     * @param bizTag 业务标识
     * @return 本实例独占的号段
     */
    @Transactional(rollbackFor = Exception.class)
    public SegmentRange allocate(String bizTag) {
        int updated = segmentMapper.updateMaxId(bizTag);
        if (updated == 0) {
            // 配置写错或建表后忘记插入种子行；给出能直接定位问题的提示
            throw new ServiceException("号段未初始化，请检查 t_segment 中是否存在 biz_tag=" + bizTag);
        }
        SegmentDO segment = segmentMapper.selectById(bizTag);
        if (segment == null || segment.getMaxId() == null
                || segment.getStep() == null || segment.getStep() <= 0) {
            throw new ServiceException("号段配置非法，biz_tag=" + bizTag);
        }
        long end = segment.getMaxId();
        long start = end - segment.getStep() + 1;
        return new SegmentRange(start, end, segment.getStep());
    }

    /**
     * 一个号段区间
     */
    @Getter
    @RequiredArgsConstructor
    public static final class SegmentRange {

        /**
         * 区间起点（含）
         */
        private final long start;

        /**
         * 区间终点（含）
         */
        private final long end;

        /**
         * 号段长度
         */
        private final int step;
    }
}
