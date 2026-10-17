package com.gugucat.shortlink.admin.common.biz.trace;

import cn.hutool.core.util.StrUtil;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪上下文
 * <p>
 * 用 MDC 承载 traceId，日志 pattern 里通过 {@code %X{traceId}} 输出。
 * 上游（网关 / Feign / MQ）传进来的 traceId 必须先校验格式：
 * 直接把它写进日志会造成日志伪造或 CRLF 注入，所以只接受
 * 长度为 8~64 的字母、数字、下划线与短横线，其余一律重新生成。
 */
public final class TraceContext {

    /**
     * 请求/响应头名称
     */
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    /**
     * MDC 中的 key
     */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private TraceContext() {
    }

    /**
     * 设置 traceId 到 MDC
     *
     * @param traceId 上游传入的 traceId，可为空
     * @return 实际生效的 traceId
     */
    public static String setTraceId(String traceId) {
        String actualTraceId = normalize(traceId);
        MDC.put(TRACE_ID_MDC_KEY, actualTraceId);
        return actualTraceId;
    }

    /**
     * 获取当前线程的 traceId
     *
     * @return traceId；不在请求线程中时为 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }

    /**
     * 清理当前线程的 traceId
     */
    public static void clear() {
        MDC.remove(TRACE_ID_MDC_KEY);
    }

    /**
     * 校验并归一化 traceId
     *
     * @param traceId 原始 traceId
     * @return 合法的原值，或新生成的 traceId
     */
    public static String normalize(String traceId) {
        if (StrUtil.isNotBlank(traceId) && VALID_TRACE_ID.matcher(traceId).matches()) {
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
