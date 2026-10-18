package com.gugucat.shortlink.gateway.controller;

import com.gugucat.shortlink.gateway.dto.GatewayErrorResult;
import com.gugucat.shortlink.gateway.util.TraceIdUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关降级响应
 * <p>
 * 熔断器打开或下游超时时，CircuitBreaker 过滤器会 forward 到 {@code /fallback}，
 * 由这个本地接口返回统一 JSON。路径不在网关路由的断言范围内，
 * 因此不会再次被转发到下游。
 */
@RestController
public class GatewayFallbackController {

    /**
     * 降级响应
     * <p>
     * 不限制 HTTP 方法：被转发的请求可能是 GET/POST/PUT/DELETE。
     *
     * @param request 转发过来的请求
     * @return 503 与统一错误体
     */
    @RequestMapping("/fallback")
    public ResponseEntity<GatewayErrorResult> fallback(ServerHttpRequest request) {
        String traceId = TraceIdUtils.normalize(
                request.getHeaders().getFirst(TraceIdUtils.TRACE_ID_HEADER));
        GatewayErrorResult result = GatewayErrorResult.builder()
                .code("C000001")
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .message("下游服务暂时不可用，请稍后再试")
                .requestId(traceId)
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }
}
