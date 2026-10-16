package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接基础访问监控实体
 * <p>
 * 逻辑表名 t_link_access_stats，未分表。唯一键是 (full_short_url, date, hour)，
 * 落库用 ON DUPLICATE KEY UPDATE 累加，所以同一短链接同一天同一小时只会有一行。
 * 也正因为唯一键里没有 weekday，weekday 只在当天第一个小时首次插入时写入，
 * 后续同一天再累加不会刷新它 —— 同一天本来也不可能有两个星期值，不影响结果。
 */
@Data
@Builder
@TableName("t_link_access_stats")
@NoArgsConstructor
@AllArgsConstructor
public class LinkAccessStatsDO extends BaseDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `full_short_url` varchar(128) COMMENT `完整短链接`
     */
    private String fullShortUrl;

    /**
     * `date` date COMMENT `日期`
     */
    private Date date;

    /**
     * `pv` int COMMENT `访问量`
     */
    private Integer pv;

    /**
     * `uv` int COMMENT `独立访客数`
     */
    private Integer uv;

    /**
     * `uip` int COMMENT `独立IP数`
     */
    private Integer uip;

    /**
     * `hour` int COMMENT `小时`
     */
    private Integer hour;

    /**
     * `weekday` int COMMENT `星期`
     */
    private Integer weekday;
}
