package com.gugucat.shortlink.admin.common.biz.user;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全配置
 * <p>
 * 失败计数与锁定阈值放在配置里而不是写死：不同环境的安全策略不同，
 * 联调时也需要临时调小阈值来验证锁定逻辑。
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.login-security")
public class LoginSecurityProperties {

    /**
     * 连续失败多少次后锁定账号
     */
    private Integer maxFailCount = 5;

    /**
     * 失败计数的统计窗口，单位分钟
     * <p>
     * 窗口内没再失败，计数自动清零，避免把很早以前的失败一直累加。
     */
    private Long failWindowMinutes = 15L;

    /**
     * 锁定时长，单位分钟
     */
    private Long lockMinutes = 15L;

    /**
     * 密码最小长度
     */
    private Integer passwordMinLength = 8;

    /**
     * 密码最大长度
     * <p>
     * 上限不宜过大：BCrypt 只处理前 72 字节，超长输入没有额外强度收益。
     */
    private Integer passwordMaxLength = 32;
}
