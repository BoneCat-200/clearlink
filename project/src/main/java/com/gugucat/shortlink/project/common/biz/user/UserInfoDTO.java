package com.gugucat.shortlink.project.common.biz.user;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息实体
 * <p>
 * 中台不查用户表，「当前用户是谁」完全来自后管通过 Feign 透传的请求头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDTO {

    /**
     * 用户 ID
     * <p>
     * 请求头里叫 userId，而后管存进 Redis 的用户 JSON 里叫 id，
     * 所以这个字段需要显式指定序列化名。
     */
    @JSONField(name = "id")
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;
}
