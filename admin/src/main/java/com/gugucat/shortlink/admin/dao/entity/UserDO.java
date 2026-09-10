package com.gugucat.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户对象
 */
@Data
@TableName("t_user_1") // 映射表名
public class UserDO {

    /**
     * `id` bigint (20) NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private String id;

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
    private String deletionTime;

    /**
     * `create_time` datetime DEFAULT NULL COMMENT `创建时间`
     */
    private String createTime;

    /**
     * `update_time` datetime DEFAULT NULL COMMENT `修改时间`
     */
    private String updateTime;

    /**
     * `del_flag` tinyint (1) DEFAULT NULL COMMENT `删除标识 0：未删除 1：已删除`
     */
    private String delFlag;

}
