package com.gugucat.shortlink.project.common.biz.trace;

import cn.hutool.core.util.StrUtil;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪上下文
 * <p>
 * 与后管模块的 {@code TraceContext} 职责一致：用 MDC 承载 traceId。
 * 中台不依赖后管，两边各保留一份，避免为了一个工具类引入公共模块。
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
     * @return traceId；不在请求/消费线程中时为 null
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
