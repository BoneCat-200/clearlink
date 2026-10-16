package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访问日志监控实体
 * <p>
 * 逻辑表名 t_link_access_logs，未分表，<b>每次跳转插一行</b> —— 这是全库增长最快的表，
 * 且没有任何清理机制。表上只有 full_short_url 的普通索引，没有唯一键，
 * 所以它记录的是「明细」，而 t_link_access_stats 那些表记录的是「按维度聚合后的计数」。
 */
@Data
@Builder
@TableName("t_link_access_logs")
@NoArgsConstructor
@AllArgsConstructor
public class LinkAccessLogsDO extends BaseDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `full_short_url` varchar(128) COMMENT `完整短链接`
     */
    private String fullShortUrl;

    /**
     * `user` varchar(64) COMMENT `用户信息`，存的是访客的 UV 标识（Cookie 里的 uv 值）
     */
    private String user;

    /**
     * `ip` varchar(64) COMMENT `IP`
     */
    private String ip;

    /**
     * `browser` varchar(64) COMMENT `浏览器`
     */
    private String browser;

    /**
     * `os` varchar(64) COMMENT `操作系统`
     */
    private String os;

    /**
     * `network` varchar(64) COMMENT `访问网络`
     */
    private String network;

    /**
     * `device` varchar(64) COMMENT `访问设备`
     */
    private String device;

    /**
     * `locale` varchar(256) COMMENT `地区`，形如「中国-北京市-北京市」
     */
    private String locale;
}
