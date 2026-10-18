package com.gugucat.shortlink.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网关错误返回体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayErrorResult {

    /**
     * 业务错误码，与下游服务的错误码规约一致（A=客户端、B=服务端、C=远程）
     * <p>
     * 401 等既有响应没有平台错误码，保持为空；限流与降级会显式给出。
     */
    private String code;

    /**
     * HTTP 状态码
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String message;

    /**
     * 请求 ID（traceId），便于按链路排查
     */
    private String requestId;
}
