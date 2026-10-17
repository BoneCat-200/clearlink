package com.gugucat.shortlink.project.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接统计实体
 * <p>
 * 跳转时采集出来的「这一次访问」的全部信息，通过消息队列交给消费者落库。
 * 之所以要把采集和落库拆开，是因为跳转是最高频的操作，不能让统计写库拖慢它。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRecordDTO {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 事件唯一标识
     * <p>
     * 一次跳转生成一个。生产者把它写到 MQ 消息的 Key 上，消费者以它作为幂等键：
     * RocketMQ 是 at-least-once 语义，失败重投不可避免，没有稳定的事件 ID 就会重复计数。
     */
    private String eventId;

    /**
     * 链路追踪 ID
     * <p>
     * 跳转请求的 traceId。消费端拿到后放进 MDC，统计落库的日志就能和跳转日志串起来。
     */
    private String traceId;

    /**
     * 访问用户IP
     */
    private String remoteAddr;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作设备
     */
    private String device;

    /**
     * 网络
     */
    private String network;

    /**
     * UV（访客标识）
     */
    private String uv;

    /**
     * 本次访问是否是该 UV 的首次访问
     */
    private Boolean uvFirstFlag;

    /**
     * 本次访问是否是该 IP 的首次访问
     */
    private Boolean uipFirstFlag;

    /**
     * 当前时间
     */
    private Date currentDate;
}
