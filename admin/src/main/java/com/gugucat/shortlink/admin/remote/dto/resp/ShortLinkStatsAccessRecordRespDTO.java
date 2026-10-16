package com.gugucat.shortlink.admin.remote.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接监控访问记录响应参数
 * <p>
 * 与中台的同名 DTO 是一份契约拷贝，字段名必须完全一致。
 * `createTime` 的 `@JsonFormat` 两边都要有：中台把它序列化成
 * `yyyy-MM-dd HH:mm:ss` 的字符串，后管反序列化时要按同样的格式解析回来。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsAccessRecordRespDTO {

    /**
     * 访客类型，取值「新访客」/「老访客」
     */
    private String uvType;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * ip
     */
    private String ip;

    /**
     * 访问网络
     */
    private String network;

    /**
     * 访问设备
     */
    private String device;

    /**
     * 地区
     */
    private String locale;

    /**
     * 用户信息，即访客的 UV 标识
     */
    private String user;

    /**
     * 访问时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
