package com.gugucat.shortlink.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 操作审计异步配置
 * <p>
 * 有界队列 + {@link ThreadPoolExecutor.CallerRunsPolicy}：
 * 队列满时退化为调用线程同步写入，形成反压，保证审计日志不会被静默丢弃。
 * 代价是极端流量下请求会多等一次写库，但审计属于合规要求，不能像统计那样直接丢。
 */
@Configuration
@EnableAsync
public class OperationLogConfiguration {

    /**
     * 审计日志线程池
     *
     * @return 线程池
     */
    @Bean("operationLogExecutor")
    public ThreadPoolTaskExecutor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("operation-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 停机时尽量把已排队的审计写完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
