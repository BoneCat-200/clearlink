package com.gugucat.shortlink.admin.common.biz.idempotent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 接口幂等配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.idempotent")
public class IdempotentProperties {

    /**
     * 是否开启幂等 Token 校验
     * <p>
     * 关闭时切面根本不会注册（见 {@link IdempotentAspect}），
     * 而不是「注册了但每个请求判断一次开关」。
     */
    private Boolean enabled = true;

    /**
     * Token 有效期，单位分钟
     * <p>
     * 只覆盖「拿到 Token 到发起请求」这段间隔，够用户填完表单即可，不宜过长。
     */
    private Long tokenTtlMinutes = 10L;
}
