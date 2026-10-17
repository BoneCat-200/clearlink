package com.gugucat.shortlink.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户操作流量风控配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.flow-limit")
public class UserFlowRiskControlConfiguration {

    /**
     * 是否开启用户流量风控
     * <p>
     * 关闭时风控过滤器根本不会被注册（见 {@code UserConfiguration}），不是「注册了但不生效」。
     */
    private Boolean enable;

    /**
     * 时间窗口，单位秒
     */
    private String timeWindow;

    /**
     * 时间窗口内允许的最大访问次数
     */
    private Long maxAccessCount;
}
