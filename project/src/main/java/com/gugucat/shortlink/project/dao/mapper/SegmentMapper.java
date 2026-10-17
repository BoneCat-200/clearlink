package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.SegmentDO;
import org.apache.ibatis.annotations.Param;

/**
 * 号段访问层
 */
public interface SegmentMapper extends BaseMapper<SegmentDO> {

    /**
     * 原子地把当前业务的最大 ID 推进一个号段
     * <p>
     * 递增的是数据库里的 {@code step} 列，不是调用方传进来的值：
     * 这样运维直接改表就能调整号段长度，不需要重启实例。
     *
     * @param bizTag 业务标识
     * @return 影响行数；0 表示该业务标识不存在
     */
    int updateMaxId(@Param("bizTag") String bizTag);
}
