package com.gugucat.shortlink.project.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gugucat.shortlink.project.dto.biz.ReconcileMismatchDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计对账报告响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileReportRespDTO {

    /**
     * 报告 ID
     */
    private Long id;

    /**
     * 业务日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bizDate;

    /**
     * 触发方式：schedule/manual
     */
    private String triggerType;

    /**
     * 手动触发的操作人
     */
    private String operator;

    /**
     * 参与对账的短链接数
     */
    private Long totalLinks;

    /**
     * PV 一致的短链接数
     */
    private Long matchedLinks;

    /**
     * PV 不一致的短链接数
     */
    private Long mismatchLinks;

    /**
     * 状态：0 执行中，1 成功，2 失败
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 不一致明细（最多 max-detail-size 条）
     */
    private List<ReconcileMismatchDTO> mismatchDetail;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 耗时（毫秒）
     */
    private Long costMs;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
