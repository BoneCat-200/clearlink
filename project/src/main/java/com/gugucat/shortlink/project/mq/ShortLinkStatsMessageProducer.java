package com.gugucat.shortlink.project.mq;

import com.gugucat.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;

/**
 * 短链接统计消息生产者
 * <p>
 * 抽出接口是为了让消息中间件**可切换**（见 `short-link.mq.type`）：
 * 本地开发零外部依赖走 Redis Stream，需要持久化/重试/死信能力时切到 RocketMQ，
 * 而调用方（跳转链路）完全不需要知道用的是哪个。
 * <p>
 * 为什么不直接把 MQ 的客户端类型暴露给调用方：跳转是最高频的链路，
 * 它只应该关心「把这次访问交出去」这一件事。
 */
public interface ShortLinkStatsMessageProducer {

    /**
     * 发送一条统计消息
     * <p>
     * 实现方对「发送失败」的处理口径应当一致：抛出去，由调用方
     * （跳转链路的 {@code recordStatsQuietly}）统一兜底为「只记日志、不影响跳转」。
     *
     * @param statsRecord 本次访问的统计信息
     */
    void send(ShortLinkStatsRecordDTO statsRecord);
}
