package com.gugucat.shortlink.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 熔断器配置
 * <p>
 * 用 Customizer 而不是依赖 Resilience4j 的自动配置：网关只引了
 * spring-cloud-starter-circuitbreaker-reactor-resilience4j，
 * 由 Spring Cloud CircuitBreaker 的工厂统一创建熔断器，
 * 这里把统计窗口、阈值与超时时间一次性设成默认配置，
 * 所有路由的熔断实例都复用这套参数。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Resilience4jConfiguration {

    private final GatewayResilienceProperties properties;

    /**
     * 默认熔断配置
     *
     * @return 熔断器工厂定制器
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        log.info("初始化网关熔断配置，slidingWindowSize={}, failureRateThreshold={}, timeoutMs={}",
                properties.getSlidingWindowSize(), properties.getFailureRateThreshold(), properties.getTimeoutMs());
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(properties.getSlidingWindowSize())
                        .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                        .failureRateThreshold(properties.getFailureRateThreshold())
                        .waitDurationInOpenState(Duration.ofMillis(properties.getWaitDurationInOpenStateMs()))
                        .permittedNumberOfCallsInHalfOpenState(properties.getPermittedNumberOfCallsInHalfOpenState())
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofMillis(properties.getTimeoutMs()))
                        .build())
                .build());
    }
}
