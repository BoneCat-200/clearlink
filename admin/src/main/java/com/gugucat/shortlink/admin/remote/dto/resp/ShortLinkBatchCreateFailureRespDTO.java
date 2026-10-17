package com.gugucat.shortlink.admin.remote.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接批量创建失败项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkBatchCreateFailureRespDTO {

    /**
     * 失败的那条原始链接
     */
    private String originUrl;

    /**
     * 失败原因
     */
    private String reason;
}
