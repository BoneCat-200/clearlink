package com.gugucat.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链接分组对象
 */
@Data
@TableName("t_group")
public class GroupDO {

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

    /**
     * `create_time` datetime DEFAULT NULL COMMENT `创建时间`
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * `update_time` datetime DEFAULT NULL COMMENT `修改时间`
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * `del_flag` tinyint (1) DEFAULT NULL COMMENT `删除标识 0：未删除 1：已删除`
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

}
