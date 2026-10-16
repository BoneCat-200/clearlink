package com.gugucat.shortlink.admin.remote.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接访客监控响应参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsUvRespDTO {

    /**
     * 统计
     */
    private Integer cnt;

    /**
     * 访客类型，取值 newUser / oldUser
     */
    private String uvType;

    /**
     * 占比，0~1 之间保留两位小数
     */
    private Double ratio;
}
