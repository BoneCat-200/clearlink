package com.gugucat.shortlink.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 后管持久层配置
 * <p>
 * 后管此前没有本地分页查询，所以一直没配分页插件；操作审计的分页查询需要它。
 * 类名与 project 模块的 {@code DataBaseConfiguration} 区分开，避免聚合部署时
 * 配置类 Bean 名冲突；插件 Bean 用 {@code @ConditionalOnMissingBean} 保证
 * 两个模块同时存在时只注册一个。
 */
@Configuration
public class AdminDataBaseConfiguration {

    /**
     * MyBatis-Plus 分页插件
     *
     * @return 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
