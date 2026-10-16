package com.gugucat.shortlink.admin.remote.dto.req;

import lombok.Data;

/**
 * 短链接监控请求参数
 * <p>
 * 与中台的同名 DTO 是一份契约拷贝，改字段要两处同步（这是重构方案里要消灭的重复）。
 */
@Data
public class ShortLinkStatsReqDTO {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 开始日期，格式 yyyy-MM-dd
     */
    private String startDate;

    /**
     * 结束日期，格式 yyyy-MM-dd
     */
    private String endDate;

    /**
     * 启用标识 0：启用 1：未启用
     */
    private Integer enableStatus;
}
