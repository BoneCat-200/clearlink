package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 操作系统统计访问实体
 * <p>
 * 逻辑表名 t_link_os_stats，未分表。唯一键是 (full_short_url, date, os)。
 */
@Data
@Builder
@TableName("t_link_os_stats")
@NoArgsConstructor
@AllArgsConstructor
public class LinkOsStatsDO extends BaseDO {

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
     * `os` varchar(64) COMMENT `操作系统`
     */
    private String os;
}
