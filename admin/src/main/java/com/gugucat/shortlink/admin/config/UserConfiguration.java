package com.gugucat.shortlink.admin.config;

import com.gugucat.shortlink.admin.common.biz.user.UserFlowRiskControlFilter;
import com.gugucat.shortlink.admin.common.biz.user.UserTransmitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用户配置自动装配
 */
@Configuration
public class UserConfiguration {

    /**
     * 用户信息传递过滤器
     */
    @Bean
    public FilterRegistrationBean<UserTransmitFilter> globalUserTransmitFilter(StringRedisTemplate stringRedisTemplate) {
        FilterRegistrationBean<UserTransmitFilter> registration = new FilterRegistrationBean<>();   // 创建注册过滤器用的Bean
        registration.setFilter(new UserTransmitFilter(stringRedisTemplate));   // 设置过滤器
        registration.addUrlPatterns("/*");   // 设置过滤器拦截的路径：所有路径
        registration.setOrder(0);   // 设置过滤器优先级
        return registration;
    }

    /**
     * 用户操作流量风控过滤器
     * <p>
     * 只在 `short-link.flow-limit.enable=true` 时注册。关闭时这个 Bean 根本不存在，
     * 不会给请求链路留下一个「每次都要判断一下是否开启」的分支。
     * <p>
     * order 必须大于用户传递过滤器（0）：风控要按「当前用户」分桶，
     * 而用户信息是 UserTransmitFilter 填进 UserContext 的。
     */
    @Bean
    @ConditionalOnProperty(name = "short-link.flow-limit.enable", havingValue = "true")
    public FilterRegistrationBean<UserFlowRiskControlFilter> globalUserFlowRiskControlFilter(
            StringRedisTemplate stringRedisTemplate,
            UserFlowRiskControlConfiguration userFlowRiskControlConfiguration) {
        FilterRegistrationBean<UserFlowRiskControlFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserFlowRiskControlFilter(stringRedisTemplate, userFlowRiskControlConfiguration));
        registration.addUrlPatterns("/*");
        registration.setOrder(10);
        return registration;
    }
}
