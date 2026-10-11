package com.gugucat.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.admin.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接分组对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_group")
public class GroupDO extends BaseDO {

    /**
     * `id` bigint (20) NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `gid` varchar (32) DEFAULT NULL COMMENT `分组标识`
     */
    private String gid;

    /**
     * `name` varchar (64) DEFAULT NULL COMMENT `分组名称`
     */
    private String name;

    /**
     * `username` varchar (256) DEFAULT NULL COMMENT `创建分组用户名`
     */
    private String username;

    /**
     * `sort_order` int (3) DEFAULT NULL COMMENT `分组排序`
     */
    private Integer sortOrder;


}
