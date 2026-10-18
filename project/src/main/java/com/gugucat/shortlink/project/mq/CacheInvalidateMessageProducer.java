package com.gugucat.shortlink.project.mq;

import com.gugucat.shortlink.project.dto.biz.CacheInvalidateMessageDTO;

/**
 * 缓存失效消息生产者
 * <p>
 * 写操作完成后发送，消费端延时删除跳转缓存。发送失败不能影响业务，
 * 第一次立即删除仍然生效，延时删除只是竞态兜底。
 */
public interface CacheInvalidateMessageProducer {

    /**
     * 发送一条缓存失效消息
     *
     * @param message 消息内容
     */
    void send(CacheInvalidateMessageDTO message);
}
