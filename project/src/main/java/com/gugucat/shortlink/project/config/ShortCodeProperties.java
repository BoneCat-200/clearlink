package com.gugucat.shortlink.project.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短码生成配置
 * <p>
 * 对应 {@code short-link.code.*}。真实的生成实现由
 * {@code short-link.code.generator} 选择（segment / hash），
 * 这一项由两个实现类上的 {@code @ConditionalOnProperty} 读取。
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.code")
public class ShortCodeProperties {

    /**
     * t_segment 中的业务标识
     */
    private String bizTag = "short-link:uri";

    /**
     * 当前号段剩余比例低于该值时异步预取下一段
     */
    private double prefetchRatio = 0.1D;

    /**
     * 仿射混淆参数
     */
    private Obfuscator obfuscator = new Obfuscator();

    /**
     * 仿射混淆参数
     * <p>
     * 上线后不能修改：改 A/B 会让已经发出的短码解码到错误的 ID。
     */
    @Data
    public static class Obfuscator {

        /**
         * 乘数，必须与 62 互质
         */
        private long a = 1_000_003L;

        /**
         * 加数
         */
        private long b = 987_654_321L;
    }
}
