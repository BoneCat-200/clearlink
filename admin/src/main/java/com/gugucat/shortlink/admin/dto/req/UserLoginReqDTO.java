package com.gugucat.shortlink.admin.dto.req;

import lombok.Data;

@Data
public class UserLoginReqDTO {
    /**
     * `username` varchar (256) DEFAULT NULL COMMENT `用户名`
     */
    private String username;

    /**
     * `password` varchar (512) DEFAULT NULL COMMENT `密码`
     */
    private String password;

}
