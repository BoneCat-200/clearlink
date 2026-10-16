package com.gugucat.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接分组实体
 * <p>
 * 逻辑表名 t_group，由 ShardingSphere 按 username 路由到 t_group_0 ~ t_group_15。
 * <p>
 * 中台这里用到分组表，只是为了校验「这个分组是不是这个用户的」——
 * 短链接管理域（admin 模块）才是分组的归属方，那边也有一份同名的实体与分片规则。
 * 两边必须用同一套分片算法：同一行数据在两处要落到同一张物理表，
 * 否则 admin 写进去的分组中台读不到。
 */
@Data
@Builder
@TableName("t_group")
@NoArgsConstructor
@AllArgsConstructor
public class GroupDO extends BaseDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `gid` varchar(32) COMMENT `分组标识`
     */
    private String gid;

    /**
     * `name` varchar(64) COMMENT `分组名称`
     */
    private String name;

    /**
     * `username` varchar(256) COMMENT `创建分组用户名`
     */
    private String username;

    /**
     * `sort_order` int COMMENT `分组排序`
     */
    private Integer sortOrder;
}
