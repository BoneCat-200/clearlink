package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 地区统计访问实体
 * <p>
 * 逻辑表名 t_link_locale_stats，未分表。唯一键是 (full_short_url, date, adcode, province)，
 * 把 adcode 也算进去是因为同名的省市可能对应不同的编码，只用名字做唯一键会误合并。
 */
@Data
@Builder
@TableName("t_link_locale_stats")
@NoArgsConstructor
@AllArgsConstructor
public class LinkLocaleStatsDO extends BaseDO {

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
     * `cnt` int COMMENT `访问量`
     */
    private Integer cnt;

    /**
     * `province` varchar(64) COMMENT `省份名称`
     */
    private String province;

    /**
     * `city` varchar(64) COMMENT `市名称`
     */
    private String city;

    /**
     * `adcode` varchar(64) COMMENT `城市编码`
     */
    private String adcode;

    /**
     * `country` varchar(64) COMMENT `国家标识`
     */
    private String country;
}
