package com.gugucat.shortlink.admin.common.convention.result;

import com.gugucat.shortlink.admin.common.biz.trace.TraceContext;
import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.gugucat.shortlink.admin.common.convention.exception.AbstractException;

import java.util.Optional;

/**
 * 全局返回对象的构造器
 * <p>
 * 统一把当前请求的 traceId 填进 {@code requestId}：
 * 调用方拿到响应后可以直接用这个值去日志里检索整条链路。
 */
public final class Results {

    /**
     * 构造空成功响应
     */
    public static Result<Void> success() {
        return new Result<Void>()
                .setCode(Result.SUCCESS_CODE)
                .setRequestId(TraceContext.getTraceId());
    }

    /**
     * 构造带返回数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setData(data)
                .setRequestId(TraceContext.getTraceId());
    }

    /**
     * 构建服务端失败响应
     */
    public static Result<Void> failure() {
        return new Result<Void>()
                .setCode(BaseErrorCode.SERVICE_ERROR.code())
                .setMessage(BaseErrorCode.SERVICE_ERROR.message())
                .setRequestId(TraceContext.getTraceId());
    }

    /**
     * 通过 {@link AbstractException} 构建失败响应
     */
    public static Result<Void> failure(AbstractException abstractException) {
        String errorCode = Optional.ofNullable(abstractException.getErrorCode())
                .orElse(BaseErrorCode.SERVICE_ERROR.code());
        String errorMessage = Optional.ofNullable(abstractException.getErrorMessage())
                .orElse(BaseErrorCode.SERVICE_ERROR.message());
        return new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage)
                .setRequestId(TraceContext.getTraceId());
    }

    /**
     * 通过 errorCode、errorMessage 构建失败响应
     */
    public static Result<Void> failure(String errorCode, String errorMessage) {
        return new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage)
                .setRequestId(TraceContext.getTraceId());
    }
}
