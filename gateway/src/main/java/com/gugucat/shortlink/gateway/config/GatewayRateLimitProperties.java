package com.gugucat.shortlink.gateway.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关限流策略配置
 * <p>
 * 按 admin / project 两套策略分别配置令牌桶：
 * 管理端请求量小、额度低；短链接中台接口调用量大、额度高。
 * 策略名会作为 RedisRateLimiter 的 routeId 拼进 Redis key，
 * 因此不同路由只要引用同一个策略就共享同一个桶。
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.gateway.rate-limit")
public class GatewayRateLimitProperties {

    /**
     * 管理端策略
     */
    private Policy admin = new Policy(20, 40, 1);

    /**
     * 短链接中台策略
     */
    private Policy project = new Policy(100, 200, 1);

    /**
     * 单套令牌桶参数
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Policy {

        /**
         * 每秒补充的令牌数
         */
        private int replenishRate;

        /**
         * 令牌桶容量，决定允许的瞬时突发量
         */
        private int burstCapacity;

        /**
         * 每次请求消耗的令牌数
         */
        private int requestedTokens = 1;
    }
}
