package com.gugucat.shortlink.project.mq.consumer;

import com.gugucat.shortlink.project.mq.handler.ShortLinkStatsMessageHandler;
import com.gugucat.shortlink.project.mq.handler.ShortLinkStatsMessageHandler.HandleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * 基于 Redis Stream 的统计消息消费者
 * <p>
 * 只做「把消息交给 handler」和「按结果决定要不要确认」，业务逻辑全在
 * {@link ShortLinkStatsMessageHandler} 里 —— 换成 RocketMQ 时这部分不用重写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "redis-stream", matchIfMissing = true)
public class ShortLinkStatsSaveConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final ShortLinkStatsMessageHandler shortLinkStatsMessageHandler;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String stream = message.getStream();
        RecordId id = message.getId();
        HandleResult result = shortLinkStatsMessageHandler.handle(message.getValue().get("statsRecord"), id.toString());
        if (result == HandleResult.SUCCESS || result == HandleResult.ALREADY_DONE) {
            acknowledgeAndDelete(stream, id);
            return;
        }
        // FAILED / IN_PROGRESS：**不确认、不删除**，消息留在消费组的待确认列表（PEL）里，
        // 由 ShortLinkStatsPendingMessageRecoveryTask 定期重投。
        // 注意这里不能再 throw：容器用的是 autoAcknowledge(false)，抛异常也只是被
        // 错误处理器记一笔日志，并不会让消息重新投递（这正是原实现的问题 ——
        // 它以为 throw 能触发重试，实际消息在 ACK 之后就被丢掉了）
    }

    /**
     * 确认并删除消息
     * <p>
     * 顺序必须是「先 ACK 再删除」：反过来的话，PEL 里会留下一个指向已删除消息的
     * 待确认项，回收任务拿到它却取不到消息体。
     *
     * @param stream Stream 键
     * @param id     消息 ID
     */
    private void acknowledgeAndDelete(String stream, RecordId id) {
        stringRedisTemplate.opsForStream().acknowledge(stream, SHORT_LINK_STATS_STREAM_GROUP_KEY, id);
        stringRedisTemplate.opsForStream().delete(stream, id.getValue());
    }

    /**
     * 供回收任务复用：确认并删除一条消息
     * <p>
     * 放在这里是因为「确认 + 删除」是 Stream 特有的操作；回收任务也走同一个语义。
     *
     * @param stream   Stream 键
     * @param recordId 消息 ID
     */
    public static void acknowledgeAndDelete(StringRedisTemplate stringRedisTemplate, String stream, RecordId recordId) {
        stringRedisTemplate.opsForStream().acknowledge(stream, SHORT_LINK_STATS_STREAM_GROUP_KEY, recordId);
        stringRedisTemplate.opsForStream().delete(stream, recordId.getValue());
    }
}
