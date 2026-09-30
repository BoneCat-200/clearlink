package com.gugucat.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gugucat.shortlink.admin.common.database.BaseDO;
import lombok.Data;

/**
 * 用户对象
 */
@Data
@TableName("t_user") // 映射表名
public class UserDO extends BaseDO {

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


}
