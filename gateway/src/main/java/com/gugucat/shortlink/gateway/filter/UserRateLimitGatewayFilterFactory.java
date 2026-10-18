package com.gugucat.shortlink.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.gugucat.shortlink.gateway.dto.GatewayErrorResult;
import com.gugucat.shortlink.gateway.util.TraceIdUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关限流过滤器
 * <p>
 * 复用 Spring Cloud Gateway 自带的 {@link RedisRateLimiter}（令牌桶 Lua 在 Redis 里原子执行），
 * 令牌桶用 Lua 在 Redis 里原子执行），只负责三件事：
 * <ol>
 *   <li>按 {@link KeyResolver} 计算出分桶键；</li>
 *   <li>调用限流器判断是否放行，并把 X-RateLimit-* 响应头透传给调用方；</li>
 *   <li>超限时返回统一的 429 JSON，而不是默认的空响应体。</li>
 * </ol>
 * 路由过滤器按配置顺序执行，本过滤器必须排在 TokenValidate 之后：
 * 否则请求头里的 username 还没被校验，攻击者可以伪造用户名绕过按用户限流。
 */
@Slf4j
@Component
public class UserRateLimitGatewayFilterFactory
        extends AbstractGatewayFilterFactory<UserRateLimitGatewayFilterFactory.Config> {

    /**
     * 业务错误码：请求过于频繁
     */
    private static final String RATE_LIMIT_CODE = "A000300";

    /**
     * 业务错误码：网关内部组件不可用
     */
    private static final String GATEWAY_ERROR_CODE = "B000001";

    private final RedisRateLimiter rateLimiter;
    private final KeyResolver userOrIpKeyResolver;

    public UserRateLimitGatewayFilterFactory(RedisRateLimiter rateLimiter, KeyResolver userOrIpKeyResolver) {
        super(Config.class);
        this.rateLimiter = rateLimiter;
        this.userOrIpKeyResolver = userOrIpKeyResolver;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route == null ? "unknown" : route.getId();
            // policy 而不是 routeId：同一策略下的多个路由共享令牌桶，
            // 策略参数在 UserRateLimitConfiguration 启动时已注册进限流器
            String policy = StringUtils.hasText(config.getPolicy()) ? config.getPolicy() : "admin";
            return userOrIpKeyResolver.resolve(exchange)
                    .defaultIfEmpty("anonymous")
                    .flatMap(key -> rateLimiter.isAllowed(policy, key))
                    .flatMap(limiterResponse -> {
                        // 把剩余令牌数等指标透传出去，便于调用方自我调节
                        limiterResponse.getHeaders().forEach((name, value) ->
                                exchange.getResponse().getHeaders().add(name, value));
                        if (limiterResponse.isAllowed()) {
                            return chain.filter(exchange);
                        }
                        return writeJson(exchange, HttpStatus.TOO_MANY_REQUESTS,
                                RATE_LIMIT_CODE, "请求过于频繁，请稍后再试");
                    })
                    .onErrorResume(ex -> {
                        // 这里兜住的是配置/编程类异常（例如策略没有注册），而不是 Redis 故障：
                        // RedisRateLimiter 内部对脚本执行失败的处理是 fail-open（放行），
                        // 因此 Redis 抖动时请求仍会通过限流。若要求故障时一律拒绝，
                        // 需要自己实现限流器，本次不改变这一业界默认取舍。
                        log.error("网关限流组件执行失败，policy: {}, routeId: {}", policy, routeId, ex);
                        return writeJson(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                GATEWAY_ERROR_CODE, "限流服务暂不可用，请稍后再试");
                    });
        };
    }

    /**
     * 写出统一 JSON 响应
     *
     * @param exchange 请求上下文
     * @param status   HTTP 状态
     * @param code     业务错误码
     * @param message  错误信息
     * @return 响应完成信号
     */
    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        // 响应可能已被下游写出（例如熔断器之外的异常），此时不能再写，避免二次提交
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdUtils.TRACE_ID_HEADER);
        GatewayErrorResult result = GatewayErrorResult.builder()
                .code(code)
                .status(status.value())
                .message(message)
                .requestId(traceId)
                .build();
        byte[] bytes = JSON.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器配置
     */
    @Data
    public static class Config {

        /**
         * 限流策略名：admin / project
         * <p>
         * 具体令牌桶参数在 short-link.gateway.rate-limit.{policy} 下配置，
         * 路由这里只引用策略，避免同一份参数在多条路由里重复维护。
         */
        private String policy = "admin";
    }
}
