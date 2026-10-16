package com.gugucat.shortlink.admin.config;

import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenFeign 调用时透传用户信息的配置
 * <p>
 * 后管调中台走的是服务间调用，不带 Cookie 也没有网关鉴权，中台无从知道「当前用户是谁」。
 * 而中台的监控统计要做归属校验（这个 gid 是不是这个用户的），所以必须把用户信息带过去。
 * <p>
 * 用户信息在 UserContext 里（由 UserTransmitFilter 从登录态解析出来），
 * 这里把它搬到请求头上；中台的 UserTransmitInterceptor 再从请求头搬回自己的 UserContext。
 * 一来一回的本质是：<b>服务间调用要显式传递上下文，隐式传递（Cookie / Session）在这一层不存在。</b>
 */
@Configuration
public class OpenFeignConfiguration {

    /**
     * 用户信息透传拦截器
     *
     * @return Feign 请求拦截器
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // 未登录时这几个值都是 null，Feign 会跳过取值为 null 的头，
            // 不会写出空的 username 头 —— 中台那边因此能正确区分「没传」和「传了空」
            template.header("username", UserContext.getUsername());
            template.header("userId", UserContext.getUserId());
            template.header("realName", UserContext.getRealName());
        };
    }
}
