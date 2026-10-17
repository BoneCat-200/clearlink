package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接变更分组记录实体
 * <p>
 * 逻辑表名 t_link_change_log，**未分表**。
 * <p>
 * 为什么需要这张表：跨分组迁移时，旧行会被置 `del_flag=1` 保留下来（含旧 gid），
 * 所以「它曾经在哪个分组」信息本身没有丢；但那是 t_link 的分片表，按新 gid 查询时
 * 根本看不到旧分组那张物理表里的行。想回答「这条短链接被谁在什么时候移到哪儿」，
 * 必须在变更发生时主动记一笔。
 */
@Data
@Builder
@TableName("t_link_change_log")
@NoArgsConstructor
@AllArgsConstructor
public class LinkChangeLogDO extends BaseDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `full_short_url` varchar(128) COMMENT `完整短链接`
     */
    private String fullShortUrl;

    /**
     * `origin_gid` varchar(32) COMMENT `原分组标识`
     */
    private String originGid;

    /**
     * `current_gid` varchar(32) COMMENT `新分组标识`
     */
    private String currentGid;

    /**
     * `operator` varchar(64) COMMENT `操作人用户名`
     * <p>
     * 中台自己不鉴权，「操作人」来自后管经 Feign 透传的 username 请求头。
     * 直接调中台（不带该头）时为 null。
     */
    private String operator;
}
