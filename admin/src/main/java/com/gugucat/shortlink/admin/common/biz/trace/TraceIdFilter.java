package com.gugucat.shortlink.admin.common.biz.trace;

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
 * 必须排在业务过滤器之前：后续的日志、Feign 透传、审计都依赖 MDC 里的 traceId。
 * 顺序取 {@link Ordered#HIGHEST_PRECEDENCE}。
 * <p>
 * 聚合部署时 admin 与 project 各有一个同名职责的过滤器，两者都会执行；
 * 第二个读到的是同一个请求头，写入相同的 traceId，不会互相影响。
 */
@Component("traceIdFilterByAdmin")  // 显式命名：聚合部署时 project 模块也有一个 TraceIdFilter
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
            // Tomcat 线程复用，不清理会让下一个请求读到上一个请求的 traceId
            TraceContext.clear();
        }
    }
}
