package com.gugucat.shortlink.project.common.biz.user;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户信息传输拦截器
 * <p>
 * 只负责「请求头里有用户信息就塞进 UserContext」，不做鉴权：
 * 调用方只有后管（经网关鉴权后由 Feign 透传三个头），中台自己不对公网暴露。
 * <p>
 * 与后管的差别是这里用拦截器而不是过滤器：中台要做归属校验的只有 Controller 这一层
 * （回收站、监控统计），过滤器会对所有请求包括跳转和静态资源生效，没有必要。
 */
@Component
public class UserTransmitInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String username = request.getHeader("username");
        if (StrUtil.isNotBlank(username)) {
            String userId = request.getHeader("userId");
            String realName = request.getHeader("realName");
            UserContext.setUser(new UserInfoDTO(userId, username, realName));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        // 必须清理：Tomcat 线程复用，不清理会让下一个请求读到上一个请求的用户信息
        UserContext.removeUser();
    }
}
