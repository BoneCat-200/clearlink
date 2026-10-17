package com.gugucat.shortlink.project.mq.consumer;

import com.gugucat.shortlink.project.mq.handler.ShortLinkStatsMessageHandler;
import com.gugucat.shortlink.project.mq.handler.ShortLinkStatsMessageHandler.HandleResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 基于 RocketMQ 的统计消息消费者
 * <p>
 * `short-link.mq.type=rocketmq` 时生效。<b>需要可用的 NameServer + Broker</b>，
 * 本仓库不内置（本地默认走 Redis Stream）。
 * <p>
 * 与 Redis Stream 实现的差别：重试与死信由 broker 负责
 * （内置 16 次指数退避，超限进入 `%DLQ%消费组名`），不再需要自己扫待确认列表。
 * 但**幂等处理器必须保留**：重投会带来重复消息，没有幂等就会重复计数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "rocketmq")
public class RocketMqStatsConsumer {

    private final ShortLinkStatsMessageHandler shortLinkStatsMessageHandler;

    @Value("${short-link.mq.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${short-link.mq.rocketmq.topic:clearlink-stats-topic}")
    private String topic;

    @Value("${short-link.mq.rocketmq.consumer-group:clearlink-stats-consumer-group}")
    private String consumerGroup;

    private DefaultMQPushConsumer consumer;

    /**
     * 应用启动时拉起消费者
     *
     * @throws MQClientException 连接 NameServer 失败
     */
    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        // 只消费启动之后的消息：统计是旁路，不需要回放历史
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt message : messages) {
                String statsRecordJson = new String(message.getBody(), StandardCharsets.UTF_8);
                HandleResult result = shortLinkStatsMessageHandler.handle(statsRecordJson, message.getMsgId());
                if (result == HandleResult.FAILED || result == HandleResult.IN_PROGRESS) {
                    // 整批重投：同批里已成功的消息会被再投一次，靠幂等处理器挡住重复计数。
                    // 这是 RocketMQ 并发消费的固有语义 —— 回调只能对整批表态。
                    // IN_PROGRESS（幂等键停在「处理中」）也重投：可能是上一轮进程崩溃留下的，
                    // 等幂等键过期后这一轮就能真正处理；直接 ACK 会把消息丢掉
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("统计消息消费者已启动，nameServer: {}, topic: {}, group: {}", nameServer, topic, consumerGroup);
    }

    /**
     * 应用关闭时释放消费者
     */
    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
