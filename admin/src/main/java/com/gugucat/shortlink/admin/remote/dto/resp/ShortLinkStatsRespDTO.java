package com.gugucat.shortlink.admin.remote.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 短链接监控响应参数
 * <p>
 * 与中台的同名 DTO 是一份契约拷贝：字段名必须完全一致，
 * 否则 Feign 反序列化时字段会静默变成 null（后管的 Jackson 配了忽略未知字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRespDTO {

    /**
     * 访问量
     */
    private Integer pv;

    /**
     * 独立访客数
     */
    private Integer uv;

    /**
     * 独立IP数
     */
    private Integer uip;

    /**
     * 基础访问详情（按天，区间内每天都有一条）
     */
    private List<ShortLinkStatsAccessDailyRespDTO> daily;

    /**
     * 地区访问详情（仅国内，按省级聚合）
     */
    private List<ShortLinkStatsLocaleCNRespDTO> localeCnStats;

    /**
     * 小时访问详情，固定 24 个元素，下标即小时
     */
    private List<Integer> hourStats;

    /**
     * 高频访问IP详情，最多 5 条
     */
    private List<ShortLinkStatsTopIpRespDTO> topIpStats;

    /**
     * 一周访问详情，固定 7 个元素，下标 0 对应周一
     */
    private List<Integer> weekdayStats;

    /**
     * 浏览器访问详情
     */
    private List<ShortLinkStatsBrowserRespDTO> browserStats;

    /**
     * 操作系统访问详情
     */
    private List<ShortLinkStatsOsRespDTO> osStats;

    /**
     * 访客访问类型详情，固定两条：newUser / oldUser
     */
    private List<ShortLinkStatsUvRespDTO> uvTypeStats;

    /**
     * 访问设备类型详情
     */
    private List<ShortLinkStatsDeviceRespDTO> deviceStats;

    /**
     * 访问网络类型详情
     */
    private List<ShortLinkStatsNetworkRespDTO> networkStats;
}
