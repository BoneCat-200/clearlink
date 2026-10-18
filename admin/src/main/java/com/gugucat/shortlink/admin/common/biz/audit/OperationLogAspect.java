package com.gugucat.shortlink.admin.common.biz.audit;

import cn.hutool.core.util.StrUtil;
import com.gugucat.shortlink.admin.common.biz.trace.TraceContext;
import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.gugucat.shortlink.admin.common.convention.exception.AbstractException;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.dao.entity.OperationLogDO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作审计切面
 * <p>
 * 采集口径：
 * <ul>
 *   <li>操作人：优先取 UserContext；登录/注册这类还没有用户上下文的接口，
 *       从请求参数的 username 字段兜底；</li>
 *   <li>结果：方法返回 {@link Result} 时取业务码与成功标识；
 *       抛 {@link AbstractException} 时取错误码与错误信息；</li>
 *   <li>参数：统一走 {@link OperationLogParamMasker} 脱敏并截断；</li>
 *   <li>链路：traceId 由 F1 的 MDC 提供，审计记录可直接关联整条请求链路。</li>
 * </ul>
 * 审计写入失败绝不影响业务：切面只负责提交，真正的落库在
 * {@link OperationLogRecorder} 里异步执行，异常也被吞掉并记日志。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogRecorder operationLogRecorder;

    /**
     * 环绕通知
     *
     * @param joinPoint    切点
     * @param operationLog 审计注解
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        HttpServletRequest request = currentRequest();
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            username = OperationLogParamMasker.extractUsername(joinPoint.getArgs());
        }
        String userId = UserContext.getUserId();
        String traceId = TraceContext.getTraceId();
        String requestParams = OperationLogParamMasker.mask(joinPoint.getArgs());
        String path = request == null ? null : request.getRequestURI();
        String requestMethod = request == null ? null : request.getMethod();
        String ip = resolveIp(request);
        // 不能使用 StrUtil.maxLength：它会在截断后追加 "..."，最多 515 个字符，
        // 写 user_agent varchar(512) 会报 Data too long，整条审计都落不了库
        String userAgent = request == null ? null : StrUtil.subPre(request.getHeader("User-Agent"), 512);

        long start = System.currentTimeMillis();
        String responseCode = Result.SUCCESS_CODE;
        boolean success = true;
        String errorMessage = null;
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Result) {
                Result<?> resultObject = (Result<?>) result;
                responseCode = resultObject.getCode();
                success = resultObject.isSuccess();
                if (!success) {
                    errorMessage = resultObject.getMessage();
                }
            }
            return result;
        } catch (Throwable ex) {
            success = false;
            if (ex instanceof AbstractException) {
                AbstractException abstractException = (AbstractException) ex;
                responseCode = abstractException.getErrorCode();
                errorMessage = abstractException.getErrorMessage();
            } else {
                responseCode = BaseErrorCode.SERVICE_ERROR.code();
                errorMessage = ex.getMessage();
            }
            throw ex;
        } finally {
            submitAuditLog(joinPoint, operationLog, traceId, username, userId, requestParams,
                    path, requestMethod, ip, userAgent, responseCode, success, errorMessage,
                    System.currentTimeMillis() - start);
        }
    }

    /**
     * 提交审计日志
     * <p>
     * 单独包一层 try/catch：即使异步线程池已关闭、注解值异常等极端情况，
     * 也不能让审计代码把业务请求带崩。
     */
    private void submitAuditLog(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                                String traceId, String username, String userId, String requestParams,
                                String path, String requestMethod, String ip, String userAgent,
                                String responseCode, boolean success, String errorMessage, long costMs) {
        try {
            OperationLogDO operationLogDO = OperationLogDO.builder()
                    .traceId(traceId)
                    // 以下字段都可能来自外部输入，按表结构宽度精确截断：
                    // 审计写入是异步的，一旦字段超长整条审计都会丢，不能只依赖业务层的默认长度
                    .username(StrUtil.subPre(username, 64))
                    .userId(userId)
                    .module(operationLog.module())
                    .action(operationLog.action())
                    .javaMethod(joinPoint.getSignature().getName())
                    .path(StrUtil.subPre(path, 256))
                    .requestMethod(requestMethod)
                    .requestParams(requestParams)
                    .responseCode(responseCode)
                    .success(success)
                    // 同上：error_message 也是 varchar(512)，必须精确截断而不是追加省略号
                    .errorMessage(StrUtil.subPre(errorMessage, 512))
                    .ip(StrUtil.subPre(ip, 64))
                    .userAgent(userAgent)
                    .costMs(costMs)
                    .createTime(LocalDateTime.now())
                    .build();
            operationLogRecorder.record(operationLogDO);
        } catch (Throwable ex) {
            log.error("提交操作审计日志失败，module: {}, action: {}",
                    operationLog.module(), operationLog.action(), ex);
        }
    }

    /**
     * 取当前请求
     *
     * @return HTTP 请求；非 Web 线程返回 null
     */
    private HttpServletRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) requestAttributes).getRequest();
        }
        return null;
    }

    /**
     * 取客户端 IP
     *
     * @param request 请求
     * @return IP
     */
    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // 代理链是「客户端, 代理1, 代理2」，第一个才是真实客户端
            return StrUtil.splitTrim(ip, ',').get(0);
        }
        ip = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
