package com.gugucat.shortlink.admin.dto.reg;

import lombok.Data;

@Data
public class UserUpdateReqDTO {
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
}
