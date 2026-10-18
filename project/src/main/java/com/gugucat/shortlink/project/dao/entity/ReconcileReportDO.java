package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计对账报告实体
 * <p>
 * 逻辑表名 t_reconcile_report，未分表。每次对账生成一行：
 * 先以「执行中」落库，任务结束后更新为成功或失败。
 */
@Data
@Builder
@TableName("t_reconcile_report")
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileReportDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * `biz_date` date COMMENT `对账业务日期`
     */
    private LocalDate bizDate;

    /**
     * `trigger_type` varchar(16) COMMENT `触发方式：schedule/manual`
     */
    private String triggerType;

    /**
     * `operator` varchar(64) COMMENT `手动触发的操作人`
     */
    private String operator;

    /**
     * `total_links` bigint COMMENT `参与对账的短链接数`
     */
    private Long totalLinks;

    /**
     * `matched_links` bigint COMMENT `PV 一致的短链接数`
     */
    private Long matchedLinks;

    /**
     * `mismatch_links` bigint COMMENT `PV 不一致的短链接数`
     */
    private Long mismatchLinks;

    /**
     * `status` tinyint COMMENT `状态：0 执行中，1 成功，2 失败`
     */
    private Integer status;

    /**
     * `mismatch_detail` text COMMENT `不一致明细 JSON`
     */
    private String mismatchDetail;

    /**
     * `error_message` varchar(512) COMMENT `失败原因`
     */
    private String errorMessage;

    /**
     * `cost_ms` bigint COMMENT `耗时（毫秒）`
     */
    private Long costMs;

    /**
     * `create_time` datetime COMMENT `创建时间`
     */
    private LocalDateTime createTime;

    /**
     * `update_time` datetime COMMENT `更新时间`
     */
    private LocalDateTime updateTime;
}
