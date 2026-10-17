package com.gugucat.shortlink.project.mq.consumer;

import cn.hutool.core.util.StrUtil;
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
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 基于 RocketMQ 的统计消息消费者
 * <p>
 * `short-link.mq.type=rocketmq` 时生效（当前默认实现）。重试与死信由 broker 负责：
 * 内置退避重试，超过 {@code max-reconsume-times} 次后进入
 * `%DLQ%clearlink-stats-consumer-group`，不再需要自己扫待确认列表。
 * 但**幂等处理器必须保留**：RocketMQ 是 at-least-once，重投必然带来重复消息。
 * <p>
 * 启动失败不阻断应用：消费统计只是旁路，跳转与建链不应该因为 MQ 不可用而不可用。
 * 启动失败后由 {@link #retryStart()} 每 30 秒重连。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "rocketmq")
public class RocketMqStatsConsumer {

    /**
     * 消费线程数下限
     */
    private static final int CONSUME_THREAD_MIN = 10;

    /**
     * 消费线程数上限
     */
    private static final int CONSUME_THREAD_MAX = 20;

    /**
     * 单次拉取并交给监听器的最大消息数
     */
    private static final int CONSUME_BATCH_SIZE = 10;

    /**
     * 单条消息的消费超时，单位分钟
     */
    private static final int CONSUME_TIMEOUT_MINUTES = 15;

    /**
     * 消费者启动失败后的重连间隔，单位毫秒
     */
    private static final long RECONNECT_DELAY_MS = 30_000L;

    private final ShortLinkStatsMessageHandler shortLinkStatsMessageHandler;

    @Value("${short-link.mq.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${short-link.mq.rocketmq.topic:clearlink-stats-topic}")
    private String topic;

    @Value("${short-link.mq.rocketmq.consumer-group:clearlink-stats-consumer-group}")
    private String consumerGroup;

    @Value("${short-link.mq.rocketmq.max-reconsume-times:16}")
    private int maxReconsumeTimes;

    private volatile DefaultMQPushConsumer consumer;

    /**
     * 应用启动时拉起消费者
     */
    @PostConstruct
    public void start() {
        try {
            doStart();
        } catch (Throwable ex) {
            log.error("统计消息消费者启动失败，{} 毫秒后重试。nameServer: {}",
                    RECONNECT_DELAY_MS, nameServer, ex);
        }
    }

    /**
     * 启动失败后的重连
     * <p>
     * 只在 consumer 尚未启动时动作；已启动的消费者由 RocketMQ 客户端自己维护长连接。
     */
    @Scheduled(fixedDelay = RECONNECT_DELAY_MS, initialDelay = RECONNECT_DELAY_MS)
    public void retryStart() {
        if (consumer != null) {
            return;
        }
        try {
            doStart();
        } catch (Throwable ex) {
            log.error("统计消息消费者重连失败，{} 毫秒后继续重试", RECONNECT_DELAY_MS, ex);
        }
    }

    /**
     * 创建并启动消费者（可重入）
     *
     * @throws MQClientException 连接 NameServer 失败
     */
    private synchronized void doStart() throws MQClientException {
        if (consumer != null) {
            return;
        }
        DefaultMQPushConsumer newConsumer = new DefaultMQPushConsumer(consumerGroup);
        newConsumer.setNamesrvAddr(nameServer);
        // 集群消费：多实例分摊消息；同一消费组内一条消息只投给一个实例
        newConsumer.setMessageModel(MessageModel.CLUSTERING);
        // 只消费启动之后的消息：统计是旁路，不需要回放历史
        newConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        newConsumer.setConsumeThreadMin(CONSUME_THREAD_MIN);
        newConsumer.setConsumeThreadMax(CONSUME_THREAD_MAX);
        newConsumer.setConsumeMessageBatchMaxSize(CONSUME_BATCH_SIZE);
        newConsumer.setMaxReconsumeTimes(maxReconsumeTimes);
        newConsumer.setConsumeTimeout(CONSUME_TIMEOUT_MINUTES);
        newConsumer.subscribe(topic, "*");
        newConsumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (int i = 0; i < messages.size(); i++) {
                MessageExt message = messages.get(i);
                String statsRecordJson = new String(message.getBody(), StandardCharsets.UTF_8);
                // 幂等键优先用生产者写入的 eventId；历史消息没有 key 时退回 msgId
                String idempotentKey = StrUtil.isNotBlank(message.getKeys())
                        ? message.getKeys()
                        : message.getMsgId();
                HandleResult result = shortLinkStatsMessageHandler.handle(statsRecordJson, idempotentKey);
                if (result == HandleResult.FAILED || result == HandleResult.IN_PROGRESS) {
                    // 只让「失败位置及之后」的消息重投：ackIndex 之前的消息 broker 会确认，
                    // 避免把同批已经成功的消息整批再投一遍。
                    // IN_PROGRESS（幂等键停在「处理中」）也重投：可能是上一轮进程崩溃留下的，
                    // 等幂等键过期后这一轮就能真正处理；直接 ACK 会把消息丢掉。
                    context.setAckIndex(i - 1);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        try {
            newConsumer.start();
        } catch (MQClientException ex) {
            try {
                newConsumer.shutdown();
            } catch (Throwable ignored) {
                // 未启动完成的消费者 shutdown 失败没有补救价值
            }
            throw ex;
        }
        this.consumer = newConsumer;
        log.info("统计消息消费者已启动，nameServer: {}, topic: {}, group: {}, maxReconsumeTimes: {}",
                nameServer, topic, consumerGroup, maxReconsumeTimes);
    }

    /**
     * 应用关闭时释放消费者
     */
    @PreDestroy
    public void shutdown() {
        DefaultMQPushConsumer currentConsumer = consumer;
        if (currentConsumer != null) {
            currentConsumer.shutdown();
        }
    }
}
