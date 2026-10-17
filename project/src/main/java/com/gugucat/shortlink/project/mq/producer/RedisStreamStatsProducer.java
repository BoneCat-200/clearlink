package com.gugucat.shortlink.project.mq.producer;

import com.alibaba.fastjson2.JSON;
import com.gugucat.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.gugucat.shortlink.project.mq.ShortLinkStatsMessageProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * 基于 Redis Stream 的统计消息生产者
 * <p>
 * `short-link.mq.type=redis-stream`（或未配置）时生效 —— 这是默认实现，
 * 零外部依赖，本地跑起来不需要装任何中间件。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "redis-stream", matchIfMissing = true)
public class RedisStreamStatsProducer implements ShortLinkStatsMessageProducer {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void send(ShortLinkStatsRecordDTO statsRecord) {
        Map<String, String> producerMap = new HashMap<>(2);
        // 消息体用 JSON 字符串而不是拆成多个 field：消费端要的是「整条记录」，
        // 拆开只会多一层拼装，且加字段时两边都要改
        producerMap.put("statsRecord", JSON.toJSONString(statsRecord));
        stringRedisTemplate.opsForStream().add(SHORT_LINK_STATS_STREAM_TOPIC_KEY, producerMap);
    }
}
