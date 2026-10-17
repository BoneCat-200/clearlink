package com.gugucat.shortlink.gateway.filter;

import com.gugucat.shortlink.gateway.util.TraceIdUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关链路追踪过滤器
 * <p>
 * 整个链路的起点：没有 traceId 就生成，有就沿用；同时写进请求头（传给下游）
 * 与响应头（方便调用方拿 traceId 来查日志）。
 * <p>
 * 必须最先执行，否则 Token 校验失败时拿不到 traceId。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceIdUtils.normalize(
                exchange.getRequest().getHeaders().getFirst(TraceIdUtils.TRACE_ID_HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TraceIdUtils.TRACE_ID_HEADER, traceId))
                .build();
        // 在响应提交前覆盖写入：下游响应里也可能带同一个头，
        // 用 set 保证调用方最终只看到一个值（即使 401/500 也能拿到 traceId）
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(TraceIdUtils.TRACE_ID_HEADER, traceId);
            return Mono.empty();
        });
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
