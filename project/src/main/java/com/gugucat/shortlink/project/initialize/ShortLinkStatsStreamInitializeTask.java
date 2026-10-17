package com.gugucat.shortlink.project.initialize;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * 初始化短链接监控消息队列的消费组
 * <p>
 * 消费组不存在时 XREADGROUP 会直接报 NOGROUP，消费者一条消息也读不到，
 * 所以在应用启动阶段先把组建出来。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.mq.type", havingValue = "redis-stream", matchIfMissing = true)
public class ShortLinkStatsStreamInitializeTask implements InitializingBean {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterPropertiesSet() {
        Boolean hasKey = stringRedisTemplate.hasKey(SHORT_LINK_STATS_STREAM_TOPIC_KEY);
        if (hasKey == null || !hasKey) {
            // Stream 本身还不存在时直接 createGroup 会失败（没有流就没有组），
            // 所以先判断一次。这个判断只在启动时做一次，竞态可以接受：
            // 两个实例同时启动、都判定为「不存在」时，后一个 createGroup 会抛
            // BUSYGROUP，而那时组已经由前一个建好了，重新启动即可，不影响功能
            stringRedisTemplate.opsForStream().createGroup(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY);
        }
    }
}
