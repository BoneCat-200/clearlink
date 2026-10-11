package com.gugucat.shortlink.admin.config;

import com.gugucat.shortlink.admin.common.biz.user.UserTransmitFilter;
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
}
