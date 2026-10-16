package com.gugucat.shortlink.admin.remote.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接地区监控响应参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsLocaleCNRespDTO {

    /**
     * 统计
     */
    private Integer cnt;

    /**
     * 地区（省级名称）
     */
    private String locale;

    /**
     * 占比，0~1 之间保留两位小数
     */
    private Double ratio;
}
