package com.gugucat.shortlink.project.config;

import com.gugucat.shortlink.project.toolkit.ShortCodeObfuscator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短码生成相关 Bean
 */
@Configuration
@RequiredArgsConstructor
public class ShortCodeConfiguration {

    private final ShortCodeProperties shortCodeProperties;

    /**
     * 短码混淆器
     * <p>
     * 在启动阶段构造：A/B 不合法时直接启动失败，而不是等到第一次建链才暴露问题。
     *
     * @return 短码混淆器
     */
    @Bean
    public ShortCodeObfuscator shortCodeObfuscator() {
        return new ShortCodeObfuscator(
                shortCodeProperties.getObfuscator().getA(),
                shortCodeProperties.getObfuscator().getB());
    }
}
