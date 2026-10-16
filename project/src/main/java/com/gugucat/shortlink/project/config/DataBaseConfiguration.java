package com.gugucat.shortlink.project.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据库持久层配置类
 */
@Configuration
public class DataBaseConfiguration {

    /**
     * 分页插件
     * <p>
     * Mapper 方法签名的返回值是 IPage、参数是 Page 子类时，靠这个拦截器改写 SQL
     * 追加 LIMIT 并发起 COUNT 查询；缺了它分页不会生效——records 会返回全部行、total 恒为 0。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
