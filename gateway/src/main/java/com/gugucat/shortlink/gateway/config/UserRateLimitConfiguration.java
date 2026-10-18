package com.gugucat.shortlink.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 限流键解析与策略注册
 */
@Configuration
@RequiredArgsConstructor
public class UserRateLimitConfiguration {

    /**
     * 策略名，与路由 filter 配置里的 policy 对应
     */
    public static final String ADMIN_POLICY = "admin";

    /**
     * 策略名，与路由 filter 配置里的 policy 对应
     */
    public static final String PROJECT_POLICY = "project";

    private final RedisRateLimiter redisRateLimiter;
    private final GatewayRateLimitProperties rateLimitProperties;

    /**
     * 启动时把两套策略注册进限流器
     * <p>
     * RedisRateLimiter 按 routeId 查找令牌桶配置；内置 RequestRateLimiter 会通过
     * FilterArgsEvent 自动注册，自定义过滤器没有这条链路，所以在启动阶段显式注册。
     * 注册发生在接收流量之前，之后只读，不存在并发修改 Map 的问题。
     */
    @PostConstruct
    public void registerPolicies() {
        RedisRateLimiter.Config adminConfig = toLimiterConfig(rateLimitProperties.getAdmin());
        RedisRateLimiter.Config projectConfig = toLimiterConfig(rateLimitProperties.getProject());
        redisRateLimiter.getConfig().put(ADMIN_POLICY, adminConfig);
        redisRateLimiter.getConfig().put(PROJECT_POLICY, projectConfig);
    }

    /**
     * 转换为限流器内部配置
     *
     * @param policy 策略参数
     * @return 限流器配置
     */
    private RedisRateLimiter.Config toLimiterConfig(GatewayRateLimitProperties.Policy policy) {
        return new RedisRateLimiter.Config()
                .setReplenishRate(policy.getReplenishRate())
                .setBurstCapacity(policy.getBurstCapacity())
                .setRequestedTokens(policy.getRequestedTokens());
    }

    /**
     * 限流分桶依据
     * <p>
     * 同时带 username 与 token 的请求按用户名分桶 —— 能走到限流过滤器，
     * 说明网关已经校验过这对凭证（路由过滤器顺序：TokenValidate → UserRateLimit）。
     * <p>
     * 登录、注册等白名单接口没有 token，按客户端 IP 分桶，
     * 避免所有匿名请求挤在同一个桶里互相影响。
     *
     * @return 限流键解析器
     */
    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String username = exchange.getRequest().getHeaders().getFirst("username");
            String token = exchange.getRequest().getHeaders().getFirst("token");
            if (StringUtils.hasText(username) && StringUtils.hasText(token)) {
                return Mono.just("user:" + username);
            }
            return Mono.just("ip:" + resolveIp(exchange));
        };
    }

    /**
     * 取客户端 IP
     * <p>
     * 经过 Nginx 后 remoteAddress 是代理地址，优先读转发头；
     * X-Forwarded-For 可能是「客户端, 代理1, 代理2」的列表，取第一个。
     *
     * @param exchange 请求上下文
     * @return 客户端 IP
     */
    private String resolveIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor) && !"unknown".equalsIgnoreCase(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress();
    }
}
