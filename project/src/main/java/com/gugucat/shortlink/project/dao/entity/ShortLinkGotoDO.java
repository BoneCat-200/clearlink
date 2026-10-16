package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接跳转实体
 * <p>
 * 逻辑表名 t_link_goto，由 ShardingSphere 按 full_short_url 路由到 t_link_goto_0 ~ t_link_goto_15。
 * 跳转时手上只有短码，拼出完整短链接后先查这张表拿到 gid，才能再去查已按 gid 分片的 t_link。
 */
@Data
@Builder
@TableName("t_link_goto")
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkGotoDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `gid` varchar (32) DEFAULT 'default' COMMENT `分组标识`
     */
    private String gid;

    /**
     * `full_short_url` varchar (128) DEFAULT NULL COMMENT `完整短链接`
     */
    private String fullShortUrl;
}
