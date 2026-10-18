package com.gugucat.shortlink.project.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统计对账汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileSummaryDTO {

    /**
     * 参与对账的短链接数
     */
    private Long totalLinks;

    /**
     * PV 不一致的短链接数
     */
    private Long mismatchLinks;
}
