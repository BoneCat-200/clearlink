package com.gugucat.shortlink.project.config;

import com.gugucat.shortlink.project.mq.consumer.ShortLinkStatsSaveConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * Redis Stream 消息队列配置
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "redis-stream", matchIfMissing = true)
public class RedisStreamConfiguration {

    private final RedisConnectionFactory redisConnectionFactory;
    private final ShortLinkStatsSaveConsumer shortLinkStatsSaveConsumer;

    /**
     * 拉取消息的线程池
     * <p>
     * 刻意配成「单线程 + 同步队列 + 丢弃最旧」：统计写入本身就是串行落库，
     * 多线程只会让同一短链接的累加语句互相抢行锁；而 SynchronousQueue
     * 配上 DiscardOldestPolicy，意味着队列里放不下多余任务时把最旧的丢掉
     * —— 统计是旁路数据，宁可丢也不愿意积压到内存里把服务拖垮。
     * <p>
     * 线程设为 daemon，是为了不阻塞应用关闭。
     *
     * @return 拉取消息用的线程池
     */
    @Bean
    public ExecutorService asyncStreamConsumer() {
        AtomicInteger index = new AtomicInteger();
        return new ThreadPoolExecutor(1,
                1,
                60,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("stream_consumer_clearlink_stats_" + index.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    /**
     * 注册统计消息的消费者
     * <p>
     * {@code @DependsOn} 是为了保证消费组已经建好再开始拉取：否则容器启动瞬间
     * 会连续报 NOGROUP（消费组不存在），虽然等组建好后能自愈，但启动日志会很难看。
     *
     * @param asyncStreamConsumer 拉取消息用的线程池
     * @return 订阅句柄
     */
    @Bean
    @DependsOn("shortLinkStatsStreamInitializeTask")
    public Subscription shortLinkStatsSaveConsumerSubscription(ExecutorService asyncStreamConsumer) {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        // 一次最多拉取多少条消息
                        .batchSize(10)
                        // 用哪个线程池执行拉取与消费
                        .executor(asyncStreamConsumer)
                        // 没拉到消息时阻塞多久。这个值不能大于 spring.data.redis.timeout，
                        // 否则会先触发连接超时
                        .pollTimeout(Duration.ofSeconds(3))
                        .build();
        StreamMessageListenerContainer.StreamReadRequest<String> streamReadRequest =
                StreamMessageListenerContainer.StreamReadRequest.builder(StreamOffset.create(SHORT_LINK_STATS_STREAM_TOPIC_KEY, ReadOffset.lastConsumed()))
                        // 消费出错时不取消订阅，继续处理后面的消息。
                        // 一条统计消息出了问题，不该让整个统计功能停下来
                        .cancelOnError(throwable -> false)
                        .consumer(Consumer.from(SHORT_LINK_STATS_STREAM_GROUP_KEY, "stats-consumer"))
                        // **关闭自动确认**：由消费者在「落库成功」之后手动 ACK。
                        // 原来开的是 true（拉取即确认），与消费者里「抛异常让消息重投」的
                        // 意图直接冲突 —— 消息在监听器返回前就被 ACK 了，异常时不会重投，
                        // 统计消息静默丢失，只剩一行 error 日志。
                        // 关掉之后，失败的消息留在待确认列表（PEL）里，
                        // 由 ShortLinkStatsPendingMessageRecoveryTask 定期重投
                        .autoAcknowledge(false)
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);
        Subscription subscription = listenerContainer.register(streamReadRequest, shortLinkStatsSaveConsumer);
        listenerContainer.start();
        return subscription;
    }
}
