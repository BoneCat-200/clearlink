package com.gugucat.shortlink.project.mq.producer;

import com.gugucat.shortlink.project.common.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 短链接监控状态保存消息队列生产者
 */
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveProducer {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短链接访问统计消息
     *
     * @param producerMap 消息内容
     */
    public void send(Map<String, String> producerMap) {
        stringRedisTemplate.opsForStream().add(RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY, producerMap);
    }
}
