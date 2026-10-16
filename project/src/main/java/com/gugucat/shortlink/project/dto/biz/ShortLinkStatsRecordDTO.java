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
