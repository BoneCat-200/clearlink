package com.gugucat.shortlink.gateway.util;

import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 网关侧的 traceId 工具
 * <p>
 * 网关基于 WebFlux，不使用 MDC；这里只负责生成/校验 traceId，
 * 真正的日志上下文由下游 Servlet 服务建立。
 */
public final class TraceIdUtils {

    /**
     * 请求/响应头名称
     */
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private TraceIdUtils() {
    }

    /**
     * 校验并归一化 traceId
     *
     * @param traceId 原始 traceId
     * @return 合法的原值，或新生成的 traceId
     */
    public static String normalize(String traceId) {
        if (StringUtils.hasText(traceId) && VALID_TRACE_ID.matcher(traceId).matches()) {
            return traceId;
        }
        return generate();
    }

    /**
     * 生成 traceId
     *
     * @return 32 位无短横线 UUID
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
