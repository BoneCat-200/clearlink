package com.gugucat.shortlink.project.mq;

import com.gugucat.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;

/**
 * 短链接统计消息生产者
 * <p>
 * 抽出接口是为了让消息中间件**可切换**（见 `short-link.mq.type`）：
 * 默认走 RocketMQ（磁盘持久化、内置重试与死信）；Redis Stream 保留为回滚实现，
 * 本地没有 broker 时改一行配置即可切回。调用方（跳转链路）完全不需要知道用的是哪个。
 * <p>
 * 为什么不直接把 MQ 的客户端类型暴露给调用方：跳转是最高频的链路，
 * 它只应该关心「把这次访问交出去」这一件事。
 */
public interface ShortLinkStatsMessageProducer {

    /**
     * 发送一条统计消息
     * <p>
     * 发送失败不能影响跳转。同步阶段的异常可以抛出，由调用方
     * （跳转链路的 {@code recordStatsQuietly}）兜底；异步发送的失败发生在回调里，
     * 实现方必须自己记录可观测的日志（带事件 ID 与短链接）。
     *
     * @param statsRecord 本次访问的统计信息
     */
    void send(ShortLinkStatsRecordDTO statsRecord);
}
