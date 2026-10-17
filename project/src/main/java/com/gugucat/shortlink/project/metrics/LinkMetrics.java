package com.gugucat.shortlink.project.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 短链接核心链路的业务指标
 * <p>
 * 通过 Micrometer 注册，最终由 {@code /actuator/prometheus} 暴露。
 * 指标名使用 `clearlink.link.*` 前缀，Prometheus 抓取时会自动转成
 * {@code clearlink_link_*_total}。
 * <p>
 * 只记录「计数」这类低成本、高价值的指标；耗时分布优先使用 HTTP 服务器指标，
 * 避免在热路径上手写计时逻辑。
 */
@Component
@RequiredArgsConstructor
public class LinkMetrics {

    private final MeterRegistry meterRegistry;

    private Counter createCounter;
    private Counter redirectRequestCounter;
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private Counter notFoundCounter;
    private Counter mqSendFailCounter;

    @PostConstruct
    public void init() {
        createCounter = Counter.builder("clearlink.link.create")
                .description("短链接创建次数")
                .register(meterRegistry);
        redirectRequestCounter = Counter.builder("clearlink.link.redirect.requests")
                .description("短链接跳转请求数（含未命中）")
                .register(meterRegistry);
        cacheHitCounter = Counter.builder("clearlink.link.redirect.cache.hit")
                .description("跳转缓存命中次数")
                .register(meterRegistry);
        cacheMissCounter = Counter.builder("clearlink.link.redirect.cache.miss")
                .description("跳转缓存未命中次数")
                .register(meterRegistry);
        notFoundCounter = Counter.builder("clearlink.link.redirect.not.found")
                .description("短链接不存在/不可跳转次数")
                .register(meterRegistry);
        mqSendFailCounter = Counter.builder("clearlink.stats.mq.send.fail")
                .description("统计消息发送失败次数")
                .register(meterRegistry);
    }

    /**
     * 记录一次建链
     */
    public void recordCreate() {
        createCounter.increment();
    }

    /**
     * 记录一次跳转请求
     */
    public void recordRedirectRequest() {
        redirectRequestCounter.increment();
    }

    /**
     * 记录一次缓存命中
     */
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    /**
     * 记录一次缓存未命中
     */
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    /**
     * 记录一次短链接不存在/不可跳转
     */
    public void recordNotFound() {
        notFoundCounter.increment();
    }

    /**
     * 记录一次统计消息发送失败
     */
    public void recordMqSendFail() {
        mqSendFailCounter.increment();
    }
}
