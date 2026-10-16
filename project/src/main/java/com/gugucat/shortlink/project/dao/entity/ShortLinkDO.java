package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接实体
 * <p>
 * 逻辑表名 t_link，由 ShardingSphere 按 gid 路由到 t_link_0 ~ t_link_15。
 * 这里做的是「表结构映射」，所以列给全，不按功能增量裁剪。
 */
@Data
@Builder
@TableName("t_link")
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkDO extends BaseDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `domain` varchar (128) DEFAULT NULL COMMENT `域名`
     */
    private String domain;

    /**
     * `short_uri` varchar (8) DEFAULT NULL COMMENT `短链接`
     */
    private String shortUri;

    /**
     * `full_short_url` varchar (128) DEFAULT NULL COMMENT `完整短链接`
     */
    private String fullShortUrl;

    /**
     * `origin_url` varchar (1024) DEFAULT NULL COMMENT `原始链接`
     */
    private String originUrl;

    /**
     * `click_num` int DEFAULT '0' COMMENT `点击量`
     */
    private Integer clickNum;

    /**
     * `gid` varchar (32) DEFAULT 'default' COMMENT `分组标识`
     */
    private String gid;

    /**
     * `favicon` varchar (256) DEFAULT NULL COMMENT `网站图标`
     */
    private String favicon;

    /**
     * `enable_status` tinyint (1) DEFAULT NULL COMMENT `启用标识 0：启用 1：未启用`
     */
    private Integer enableStatus;

    /**
     * `created_type` tinyint (1) DEFAULT NULL COMMENT `创建类型 0：接口创建 1：控制台创建`
     */
    private Integer createdType;

    /**
     * `valid_date_type` tinyint (1) DEFAULT NULL COMMENT `有效期类型 0：永久有效 1：自定义`
     */
    private Integer validDateType;

    /**
     * `valid_date` datetime DEFAULT NULL COMMENT `有效期`
     */
    private Date validDate;

    /**
     * `describe` varchar (1024) DEFAULT NULL COMMENT `描述`
     * <p>
     * describe 是 MySQL 关键字，必须反引号包裹，否则生成的 SQL 语法错误
     */
    @TableField("`describe`")
    private String describe;

    /**
     * `total_pv` int DEFAULT NULL COMMENT `历史PV`
     */
    private Integer totalPv;

    /**
     * `total_uv` int DEFAULT NULL COMMENT `历史UV`
     */
    private Integer totalUv;

    /**
     * `total_uip` int DEFAULT NULL COMMENT `历史UIP`
     */
    private Integer totalUip;

    /**
     * `del_time` bigint DEFAULT '0' COMMENT `删除时间戳`
     */
    private Long delTime;
}
