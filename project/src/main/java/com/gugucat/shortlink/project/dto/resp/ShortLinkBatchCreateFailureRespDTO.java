package com.gugucat.shortlink.project.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接批量创建失败项
 * <p>
 * 参考实现的批量创建对失败项只打日志、不返回，调用方只能看到「成功了几条」，
 * 既不知道是哪条失败，也不知道原因。这里把失败明细带回去。
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
     * 失败原因（业务异常取可读消息，其它异常取异常信息）
     */
    private String reason;
}
