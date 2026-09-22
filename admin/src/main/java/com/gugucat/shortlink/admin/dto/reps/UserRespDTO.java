package com.gugucat.shortlink.admin.dto.reps;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gugucat.shortlink.admin.common.serialize.PhoneDesensitizationSerializer;
import lombok.Data;

/**
 * 用户返回参数响应对象
 */
@Data
public class UserRespDTO {

    /**
     * `id` bigint (20) NOT NULL AUTO_INCREMENT COMMENT `ID`
     */
    private String id;

    /**
     * `username` varchar (256) DEFAULT NULL COMMENT `用户名`
     */
    private String username;

    /**
     * `real_name` varchar (256) DEFAULT NULL COMMENT `真实姓名`
     */
    private String realName;

    /**
     * `phone` varchar (128) DEFAULT NULL COMMENT `手机号`
     */
    @JsonSerialize(using = PhoneDesensitizationSerializer.class)    // 指定序列化器：手机号脱敏
    private String phone;

    /**
     * `mail` varchar (512) DEFAULT NULL COMMENT `邮箱`
     */
    private String mail;

}
