package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 号段实体
 * <p>
 * `t_segment` 是单表，不参与分片：它是发号器的元数据，不是业务数据，
 * 所有实例都必须读到同一行才能保证号段不重叠。
 */
@Data
@TableName("t_segment")
public class SegmentDO {

    /**
     * `biz_tag` varchar(64) NOT NULL COMMENT '业务标识'
     */
    @TableId(value = "biz_tag", type = IdType.INPUT)
    private String bizTag;

    /**
     * `max_id` bigint NOT NULL COMMENT '已分配到的最大 ID'
     */
    private Long maxId;

    /**
     * `step` int NOT NULL COMMENT '号段长度'
     * <p>
     * 以数据库里的值为准，运维可以按 QPS 调整，不需要改代码或重启。
     */
    private Integer step;

    /**
     * `create_time` datetime COMMENT '创建时间'
     */
    private LocalDateTime createTime;

    /**
     * `update_time` datetime COMMENT '更新时间'
     */
    private LocalDateTime updateTime;
}
