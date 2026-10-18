package com.gugucat.shortlink.project.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统计对账不一致明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileMismatchDTO {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 小时统计表累加出的 PV
     */
    private Long accessPv;

    /**
     * 当日汇总表中的 PV
     */
    private Long todayPv;

    /**
     * 差值：accessPv - todayPv
     */
    private Long diff;
}
