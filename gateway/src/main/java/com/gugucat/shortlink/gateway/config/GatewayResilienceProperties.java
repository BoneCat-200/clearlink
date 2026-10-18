package com.gugucat.shortlink.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关熔断与超时配置
 * <p>
 * 参数沿用 Resilience4j 的语义：滑动窗口统计失败率，达到阈值后打开熔断器；
 * 打开时长结束后进入半开状态，放少量请求试探下游是否恢复。
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.gateway.resilience")
public class GatewayResilienceProperties {

    /**
     * 滑动窗口大小（按调用次数统计）
     */
    private Integer slidingWindowSize = 20;

    /**
     * 窗口内至少多少次调用才开始计算失败率
     */
    private Integer minimumNumberOfCalls = 10;

    /**
     * 失败率阈值（百分比），达到后打开熔断器
     */
    private Float failureRateThreshold = 50F;

    /**
     * 熔断器打开后保持多久，单位毫秒
     */
    private Long waitDurationInOpenStateMs = 10000L;

    /**
     * 半开状态下允许放行的探测请求数
     */
    private Integer permittedNumberOfCallsInHalfOpenState = 3;

    /**
     * 单次调用的超时时间，单位毫秒
     * <p>
     * 应小于网关的 response-timeout：让 Resilience4j 先触发超时并走统一的降级响应。
     */
    private Long timeoutMs = 4000L;
}
