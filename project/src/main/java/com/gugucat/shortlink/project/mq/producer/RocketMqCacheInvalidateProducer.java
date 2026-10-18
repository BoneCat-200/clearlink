package com.gugucat.shortlink.project.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.gugucat.shortlink.project.common.biz.trace.TraceContext;
import com.gugucat.shortlink.project.dto.biz.CacheInvalidateMessageDTO;
import com.gugucat.shortlink.project.metrics.LinkMetrics;
import com.gugucat.shortlink.project.mq.CacheInvalidateMessageProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 基于 RocketMQ 的缓存失效消息生产者
 * <p>
 * 发送延时消息而不是立即消费：写操作完成后的第一次删除已经在业务代码里做了，
 * 这里要删的是「第一次删除之后又被读请求回填的旧值」，所以必须等一小段时间。
 * <p>
 * 启动失败不阻断应用；首次发送时会再次尝试启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.cache-invalidate.enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqCacheInvalidateProducer implements CacheInvalidateMessageProducer {

    /**
     * 发送超时，单位毫秒
     */
    private static final int SEND_TIMEOUT_MS = 3000;

    /**
     * 异步发送失败后的重试次数
     */
    private static final int ASYNC_RETRY_TIMES = 3;

    /**
     * 消息 Tag
     */
    private static final String MESSAGE_TAG = "cache-invalidate";

    private final LinkMetrics linkMetrics;

    @Value("${short-link.mq.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${short-link.cache-invalidate.topic:clearlink-cache-invalidate-topic}")
    private String topic;

    @Value("${short-link.cache-invalidate.producer-group:clearlink-cache-invalidate-producer-group}")
    private String producerGroup;

    /**
     * RocketMQ 延时等级：默认等级表为 1s 5s 10s 30s 1m ...
     * 2 对应 5 秒
     */
    @Value("${short-link.cache-invalidate.delay-level:2}")
    private int delayLevel;

    private volatile DefaultMQProducer producer;

    @PostConstruct
    public void start() {
        try {
            ensureStarted();
        } catch (Throwable ex) {
            log.error("缓存失效消息生产者启动失败，首次发送时将重试。nameServer: {}", nameServer, ex);
        }
    }

    /**
     * 确保生产者已启动（懒启动，可重入）
     *
     * @throws MQClientException 连接 NameServer 失败
     */
    private synchronized void ensureStarted() throws MQClientException {
        if (producer != null) {
            return;
        }
        DefaultMQProducer newProducer = new DefaultMQProducer(producerGroup);
        newProducer.setNamesrvAddr(nameServer);
        newProducer.setSendMsgTimeout(SEND_TIMEOUT_MS);
        newProducer.setRetryTimesWhenSendAsyncFailed(ASYNC_RETRY_TIMES);
        try {
            newProducer.start();
        } catch (MQClientException ex) {
            try {
                newProducer.shutdown();
            } catch (Throwable ignored) {
                // 未启动完成的客户端 shutdown 失败没有补救价值
            }
            throw ex;
        }
        this.producer = newProducer;
        log.info("缓存失效消息生产者已启动，nameServer: {}, topic: {}, group: {}, delayLevel: {}",
                nameServer, topic, producerGroup, delayLevel);
    }

    @PreDestroy
    public void shutdown() {
        DefaultMQProducer currentProducer = producer;
        if (currentProducer != null) {
            currentProducer.shutdown();
        }
    }

    @Override
    public void send(CacheInvalidateMessageDTO message) {
        try {
            ensureStarted();
            String traceId = StrUtil.isNotBlank(message.getTraceId())
                    ? message.getTraceId()
                    : TraceContext.getTraceId();
            message.setTraceId(traceId);
            Message mqMessage = new Message(
                    topic,
                    MESSAGE_TAG,
                    message.getFullShortUrl(),
                    JSON.toJSONString(message).getBytes(StandardCharsets.UTF_8));
            // Key 用短链接：排查时可以直接按短链接查消息
            mqMessage.setKeys(message.getFullShortUrl());
            // 延时等级由 broker 的 messageDelayLevel 决定，默认 2 = 5 秒
            mqMessage.setDelayTimeLevel(delayLevel);
            if (StrUtil.isNotBlank(traceId)) {
                mqMessage.putUserProperty("traceId", traceId);
            }
            producer.send(mqMessage, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 成功不打日志：写操作频率不高，但也没有必要每次写一条
                }

                @Override
                public void onException(Throwable ex) {
                    linkMetrics.recordCacheInvalidateSendFail();
                    log.error("发送缓存失效消息失败，fullShortUrl: {}, reason: {}",
                            message.getFullShortUrl(), message.getReason(), ex);
                }
            });
        } catch (Throwable ex) {
            linkMetrics.recordCacheInvalidateSendFail();
            log.error("提交缓存失效消息失败，fullShortUrl: {}, reason: {}",
                    message.getFullShortUrl(), message.getReason(), ex);
        }
    }
}
