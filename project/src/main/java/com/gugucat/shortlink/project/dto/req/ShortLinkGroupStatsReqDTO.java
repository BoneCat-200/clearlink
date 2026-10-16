package com.gugucat.shortlink.project.dto.req;

import lombok.Data;

/**
 * 分组短链接监控请求参数
 * <p>
 * 与单个短链接的监控参数（{@link ShortLinkStatsReqDTO}）相比少了两个字段，
 * 都是刻意少的：
 * <ul>
 *   <li>没有 {@code fullShortUrl} —— 统计对象是整个分组</li>
 *   <li>没有 {@code enableStatus} —— 分组统计一律只看正常状态的短链接，
 *       回收站里的不该计入分组数据，所以查询里写死 {@code enable_status = '0'}</li>
 * </ul>
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
