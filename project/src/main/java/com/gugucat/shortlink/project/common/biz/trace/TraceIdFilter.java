package com.gugucat.shortlink.project.common.biz.trace;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 链路追踪过滤器
 * <p>
 * 中台的跳转链路也经过它，所以短链跳转的日志同样带 traceId。
 * 必须排在其它过滤器/拦截器之前。
 */
@Component("traceIdFilterByProject")  // 显式命名：聚合部署时 admin 模块也有一个 TraceIdFilter
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        String traceId = TraceContext.setTraceId(
                httpServletRequest.getHeader(TraceContext.TRACE_ID_HEADER));
        httpServletResponse.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
