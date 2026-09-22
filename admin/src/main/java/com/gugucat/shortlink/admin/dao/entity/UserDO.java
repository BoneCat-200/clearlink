package com.gugucat.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户对象
 */
@Data
@TableName("t_user_0") // 映射表名
public class UserDO {

    /**
     * `id` bigint (20) NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private Long id;

    /**
     * `username` varchar (256) DEFAULT NULL COMMENT `用户名`
     */
    private String username;

    /**
     * `password` varchar (512) DEFAULT NULL COMMENT `密码`
     */
    private String password;

    /**
     * `real_name` varchar (256) DEFAULT NULL COMMENT `真实姓名`
     */
    private String realName;

    /**
     * `phone` varchar (128) DEFAULT NULL COMMENT `手机号`
     */
    private String phone;

    /**
     * `mail` varchar (512) DEFAULT NULL COMMENT `邮箱`
     */
    private String mail;

    /**
     * `deletion_time` bigint (20) DEFAULT NULL COMMENT `注销时间戳`
     */
    private Long deletionTime;

    /**
     * `create_time` datetime DEFAULT NULL COMMENT `创建时间`
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * `update_time` datetime DEFAULT NULL COMMENT `修改时间`
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updateTime;

    /**
     * `del_flag` tinyint (1) DEFAULT NULL COMMENT `删除标识 0：未删除 1：已删除`
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

}
