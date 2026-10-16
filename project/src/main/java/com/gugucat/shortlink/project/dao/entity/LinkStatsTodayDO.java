package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接今日统计实体
 * <p>
 * 逻辑表名 t_link_stats_today，未分表。唯一键是 (full_short_url, date)。
 * <p>
 * 这张表存的是 t_link_access_stats 的「今日汇总」，看起来冗余，但短链接列表页
 * 要按今日 PV 排序并展示，逐行去 access_stats 里聚合在列表场景下代价太高，
 * 用一张按天一行的小表换列表页的一次简单关联。
 */
@Data
@Builder
@TableName("t_link_stats_today")
@NoArgsConstructor
@AllArgsConstructor
public class LinkStatsTodayDO extends BaseDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `full_short_url` varchar(128) COMMENT `短链接`
     */
    private String fullShortUrl;

    /**
     * `date` date COMMENT `日期`
     */
    private Date date;

    /**
     * `today_pv` int COMMENT `今日PV`
     */
    private Integer todayPv;

    /**
     * `today_uv` int COMMENT `今日UV`
     */
    private Integer todayUv;

    /**
     * `today_uip` int COMMENT `今日IP数`
     */
    private Integer todayUip;
}
