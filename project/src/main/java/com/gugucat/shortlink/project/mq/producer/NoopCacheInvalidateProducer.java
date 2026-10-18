package com.gugucat.shortlink.project.mq.producer;

import com.gugucat.shortlink.project.dto.biz.CacheInvalidateMessageDTO;
import com.gugucat.shortlink.project.mq.CacheInvalidateMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 关闭延时双删时的空实现
 * <p>
 * 显式关闭 `short-link.cache-invalidate.enabled` 后仍然注入这个 Bean，
 * 业务代码不需要为空开关写分支；第一次立即删除缓存仍然生效。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.cache-invalidate.enabled", havingValue = "false")
public class NoopCacheInvalidateProducer implements CacheInvalidateMessageProducer {

    @Override
    public void send(CacheInvalidateMessageDTO message) {
        log.debug("延时双删已关闭，跳过缓存失效消息，fullShortUrl: {}", message.getFullShortUrl());
    }
}
