package com.gugucat.shortlink.project.mq.consumer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.gugucat.shortlink.project.dto.biz.CacheInvalidateMessageDTO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;

/**
 * 基于 RocketMQ 的缓存失效消息消费者
 * <p>
 * 收到消息后删除跳转缓存与空值缓存。删除是幂等的，重复消费不会产生副作用，
 * 因此不需要像统计消息那样使用幂等键；失败交给 RocketMQ 重试即可。
 * <p>
 * 启动失败不阻断应用，每 30 秒重连。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.cache-invalidate.enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqCacheInvalidateConsumer {

    private static final int CONSUME_THREAD_MIN = 4;

    private static final int CONSUME_THREAD_MAX = 8;

    private static final int CONSUME_BATCH_SIZE = 10;

    private static final int CONSUME_TIMEOUT_MINUTES = 5;

    private static final long RECONNECT_DELAY_MS = 30_000L;

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${short-link.mq.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${short-link.cache-invalidate.topic:clearlink-cache-invalidate-topic}")
    private String topic;

    @Value("${short-link.cache-invalidate.consumer-group:clearlink-cache-invalidate-consumer-group}")
    private String consumerGroup;

    @Value("${short-link.cache-invalidate.max-reconsume-times:8}")
    private int maxReconsumeTimes;

    private volatile DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() {
        try {
            doStart();
        } catch (Throwable ex) {
            log.error("缓存失效消息消费者启动失败，{} 毫秒后重试。nameServer: {}",
                    RECONNECT_DELAY_MS, nameServer, ex);
        }
    }

    /**
     * 启动失败后的重连
     */
    @Scheduled(fixedDelay = RECONNECT_DELAY_MS, initialDelay = RECONNECT_DELAY_MS)
    public void retryStart() {
        if (consumer != null) {
            return;
        }
        try {
            doStart();
        } catch (Throwable ex) {
            log.error("缓存失效消息消费者重连失败，{} 毫秒后继续重试", RECONNECT_DELAY_MS, ex);
        }
    }

    private synchronized void doStart() throws MQClientException {
        if (consumer != null) {
            return;
        }
        DefaultMQPushConsumer newConsumer = new DefaultMQPushConsumer(consumerGroup);
        newConsumer.setNamesrvAddr(nameServer);
        newConsumer.setMessageModel(MessageModel.CLUSTERING);
        // 只消费启动之后的消息：历史失效消息对应的缓存早已被第一次删除覆盖
        newConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        newConsumer.setConsumeThreadMin(CONSUME_THREAD_MIN);
        newConsumer.setConsumeThreadMax(CONSUME_THREAD_MAX);
        newConsumer.setConsumeMessageBatchMaxSize(CONSUME_BATCH_SIZE);
        newConsumer.setMaxReconsumeTimes(maxReconsumeTimes);
        newConsumer.setConsumeTimeout(CONSUME_TIMEOUT_MINUTES);
        newConsumer.subscribe(topic, "*");
        newConsumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt message : messages) {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                CacheInvalidateMessageDTO invalidateMessage;
                try {
                    invalidateMessage = JSON.parseObject(body, CacheInvalidateMessageDTO.class);
                } catch (Throwable ex) {
                    // 消息体损坏，重试也不会变好；直接 ACK，避免毒消息无限重投
                    log.error("缓存失效消息格式错误，已丢弃。msgId: {}, body: {}", message.getMsgId(), body, ex);
                    continue;
                }
                if (invalidateMessage == null || StrUtil.isBlank(invalidateMessage.getFullShortUrl())) {
                    log.warn("缓存失效消息缺少 fullShortUrl，已丢弃。msgId: {}", message.getMsgId());
                    continue;
                }
                try {
                    String fullShortUrl = invalidateMessage.getFullShortUrl();
                    stringRedisTemplate.delete(List.of(
                            String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                            String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl)));
                    log.info("延时删除短链缓存完成，fullShortUrl: {}, reason: {}, traceId: {}",
                            fullShortUrl, invalidateMessage.getReason(), invalidateMessage.getTraceId());
                } catch (Throwable ex) {
                    log.error("延时删除短链缓存失败，等待重试。fullShortUrl: {}",
                            invalidateMessage.getFullShortUrl(), ex);
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
        log.info("缓存失效消息消费者已启动，nameServer: {}, topic: {}, group: {}, maxReconsumeTimes: {}",
                nameServer, topic, consumerGroup, maxReconsumeTimes);
    }

    @PreDestroy
    public void shutdown() {
        DefaultMQPushConsumer currentConsumer = consumer;
        if (currentConsumer != null) {
            currentConsumer.shutdown();
        }
    }
}
