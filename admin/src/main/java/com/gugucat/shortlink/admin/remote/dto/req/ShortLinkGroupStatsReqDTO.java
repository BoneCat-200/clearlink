package com.gugucat.shortlink.admin.remote.dto.req;

import lombok.Data;

/**
 * 分组短链接监控请求参数
 * <p>
 * 与中台的同名 DTO 是一份契约拷贝，改字段要两处同步。
 * 比单个短链接的监控参数少 `fullShortUrl` 与 `enableStatus`：
 * 统计对象是整个分组，且分组统计一律只算正常状态的短链接。
 */
@Data
public class ShortLinkGroupStatsReqDTO {

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
}
