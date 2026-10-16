package com.gugucat.shortlink.admin.common.biz.user;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;

/**
 * 用户信息传输过滤器
 * <p>
 * 只负责「请求头里有登录态就塞进 UserContext」，不负责鉴权——
 * 鉴权由网关的 TokenValidate 过滤器完成，未带有效 Token 的请求在网关就被 401 拦掉了。
 * 因此这里拿不到登录态时只是「未登录」，不抛异常。
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String userName = httpServletRequest.getHeader("username");
        String token = httpServletRequest.getHeader("token");
        // 必须先判空：null 的 hashKey 会让 Redis 抛 IllegalArgumentException: non null hash key required，
        // 请求会在过滤器里直接 500，且返回的是 Spring 默认错误体而不是统一 Result
        if (StrUtil.isNotBlank(userName) && StrUtil.isNotBlank(token)) {
            Object userInfoJsonStr = stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + userName, token);
            if (userInfoJsonStr != null) {
                UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
                UserContext.setUser(userInfoDTO);
            }
        }
        try {
            filterChain.doFilter(servletRequest, servletResponse);  // 继续执行下一个过滤器或目标资源
        } finally {
            UserContext.removeUser();   // 移除用户信息, 避免内存泄漏
        }
    }
}
