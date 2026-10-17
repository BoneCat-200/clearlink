package com.gugucat.shortlink.project.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.gugucat.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.gugucat.shortlink.project.mq.ShortLinkStatsMessageProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.UUID;

/**
 * 基于 RocketMQ 的统计消息生产者
 * <p>
 * `short-link.mq.type=rocketmq` 时生效（当前默认实现）。需要可用的 NameServer + Broker，
 * 本地启动方式见 `deploy/rocketmq/README.md`。
 * <p>
 * 用<b>异步发送</b>而不是同步：跳转是最高频的链路，同步发送会额外增加一次网络往返；
 * 统计是旁路，多等这一下没有价值。异步发送的失败只能在回调里记日志。
 * <p>
 * 消息 Key 写入事件的 {@code eventId}，消费端用它做幂等键 —— 这与 RPC 的 traceId 类似：
 * 业务事件在进入 MQ 之前就应该有稳定标识，而不是依赖每个中间件各自生成的消息 ID。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "rocketmq")
public class RocketMqStatsProducer implements ShortLinkStatsMessageProducer {

    /**
     * 发送超时，单位毫秒
     */
    private static final int SEND_TIMEOUT_MS = 3000;

    /**
     * 异步发送失败后的重试次数
     */
    private static final int ASYNC_RETRY_TIMES = 3;

    /**
     * 消息 Tag，便于按类型订阅与过滤
     */
    private static final String MESSAGE_TAG = "stats";

    @Value("${short-link.mq.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${short-link.mq.rocketmq.topic:clearlink-stats-topic}")
    private String topic;

    @Value("${short-link.mq.rocketmq.producer-group:clearlink-stats-producer-group}")
    private String producerGroup;

    private volatile DefaultMQProducer producer;

    /**
     * 应用启动时拉起生产者
     * <p>
     * 启动失败只记日志、不阻断应用：短链跳转是本服务的核心链路，
     * 不能因为统计 MQ 不可用就让整个服务起不来。首次发送时会再次尝试启动。
     */
    @PostConstruct
    public void start() {
        try {
            ensureStarted();
        } catch (Throwable ex) {
            log.error("统计消息生产者启动失败，首次发送时将重试。nameServer: {}", nameServer, ex);
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
            // 启动到一半失败时清理客户端线程，避免每次重试都泄漏一组线程
            try {
                newProducer.shutdown();
            } catch (Throwable ignored) {
                // 未启动完成的客户端 shutdown 失败没有补救价值
            }
            throw ex;
        }
        this.producer = newProducer;
        log.info("统计消息生产者已启动，nameServer: {}, topic: {}, group: {}", nameServer, topic, producerGroup);
    }

    /**
     * 应用关闭时释放生产者
     */
    @PreDestroy
    public void shutdown() {
        DefaultMQProducer currentProducer = producer;
        if (currentProducer != null) {
            currentProducer.shutdown();
        }
    }

    @Override
    public void send(ShortLinkStatsRecordDTO statsRecord) {
        try {
            ensureStarted();
            String eventId = statsRecord.getEventId();
            if (StrUtil.isBlank(eventId)) {
                // 正常路径由跳转采集时生成 eventId；这里兜底兼容没有该字段的调用方
                eventId = UUID.randomUUID().toString();
                log.warn("统计事件缺少 eventId，已临时生成：{}, fullShortUrl: {}", eventId, statsRecord.getFullShortUrl());
            }
            Message message = new Message(topic, MESSAGE_TAG, eventId,
                    JSON.toJSONString(statsRecord).getBytes(StandardCharsets.UTF_8));
            // keys 同时用于消费端幂等与按事件查询
            message.setKeys(eventId);
            String finalEventId = eventId;
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 成功不打日志：这是最高频的链路，成功日志会把日志盘写满
                }

                @Override
                public void onException(Throwable ex) {
                    log.error("发送统计消息失败，eventId: {}, fullShortUrl: {}",
                            finalEventId, statsRecord.getFullShortUrl(), ex);
                }
            });
        } catch (Throwable ex) {
            // 提交阶段的同步异常（生产者启动失败、消息体非法等）
            log.error("提交统计消息失败，eventId: {}, fullShortUrl: {}",
                    statsRecord.getEventId(), statsRecord.getFullShortUrl(), ex);
        }
    }
}
