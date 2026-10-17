package com.gugucat.shortlink.project.mq.producer;

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

/**
 * 基于 RocketMQ 的统计消息生产者
 * <p>
 * `short-link.mq.type=rocketmq` 时生效。<b>需要可用的 NameServer + Broker</b>；
 * 本仓库没有内置 broker，本地默认走 Redis Stream（见 application.yaml）。
 * <p>
 * 用<b>异步发送</b>而不是同步：跳转是最高频的链路，同步发送会额外增加一次网络往返
 * 的延迟；统计是旁路，多等这一下没有价值。异步发送的失败在回调里记日志。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "rocketmq")
public class RocketMqStatsProducer implements ShortLinkStatsMessageProducer {

    /**
     * 生产者组名
     */
    private static final String PRODUCER_GROUP = "clearlink-stats-producer-group";

    @Value("${short-link.mq.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${short-link.mq.rocketmq.topic:clearlink-stats-topic}")
    private String topic;

    private DefaultMQProducer producer;

    /**
     * 应用启动时拉起生产者
     *
     * @throws MQClientException 连接 NameServer 失败
     */
    @PostConstruct
    public void start() throws MQClientException {
        producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(nameServer);
        producer.start();
        log.info("统计消息生产者已启动，nameServer: {}, topic: {}", nameServer, topic);
    }

    /**
     * 应用关闭时释放生产者
     */
    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
        }
    }

    @Override
    public void send(ShortLinkStatsRecordDTO statsRecord) {
        Message message = new Message(topic, JSON.toJSONString(statsRecord).getBytes(StandardCharsets.UTF_8));
        try {
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 成功不打日志：这是最高频的链路，成功日志会把日志盘写满
                }

                @Override
                public void onException(Throwable ex) {
                    log.error("发送统计消息失败，fullShortUrl: {}", statsRecord.getFullShortUrl(), ex);
                }
            });
        } catch (Throwable ex) {
            // 异步发送阶段的同步异常（如生产者未启动、消息体非法）
            log.error("提交统计消息失败，fullShortUrl: {}", statsRecord.getFullShortUrl(), ex);
        }
    }
}
