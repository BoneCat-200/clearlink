package com.gugucat.shortlink.admin.common.biz.idempotent;

import cn.hutool.core.util.StrUtil;
import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 一次性幂等 Token 切面
 * <p>
 * 执行顺序放在审计切面外层：Token 校验不通过时直接拒绝，不进入业务，也不产生审计记录；
 * 业务失败时审计仍会照常记录。
 * <p>
 * 失败处理的口径：
 * <ul>
 *   <li>业务抛 {@link ClientException} 或返回失败的 {@link Result}：说明是参数/前置条件问题，
 *       业务已回滚且没有副作用，按原剩余有效期回填 Token，允许调用方修正后重试；</li>
 *   <li>系统异常、远程调用异常：结果不确定（可能已经写入），<b>不回填</b>，
 *       宁可让调用方重新取 Token，也不冒重复执行的风险。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.idempotent.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotentAspect {

    private final IdempotentTokenService idempotentTokenService;

    /**
     * 环绕通知
     *
     * @param joinPoint  切点
     * @param idempotent 幂等注解
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        String token = request == null ? null : request.getHeader(IdempotentTokenService.TOKEN_HEADER);
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            throw new ClientException("用户未登录");
        }
        if (StrUtil.isBlank(token)) {
            throw new ClientException(BaseErrorCode.IDEMPOTENT_TOKEN_NULL_ERROR);
        }
        // 消费是原子的：并发请求里只有一个能成功删掉 Token，其余都会在这里被拒
        long remainMillis = idempotentTokenService.consume(token, username);
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Result && !((Result<?>) result).isSuccess()
                    && isClientFailure((Result<?>) result)) {
                restoreQuietly(token, username, remainMillis);
            }
            return result;
        } catch (ClientException ex) {
            restoreQuietly(token, username, remainMillis);
            throw ex;
        }
    }

    /**
     * 判断失败是否是「客户端侧、可安全重试」的失败
     * <p>
     * 错误码以 A 开头表示参数/前置条件类客户端错误，业务已回滚；
     * B（服务端）与 C（远程调用）开头的结果可能是「已经写了一半」，
     * 回填 Token 会让调用方重试，存在重复写入风险，因此不回填。
     *
     * @param result 业务返回体
     * @return 是否允许用同一个 Token 重试
     */
    private boolean isClientFailure(Result<?> result) {
        String code = result.getCode();
        return code != null && code.startsWith("A");
    }

    /**
     * 回填 Token，回填失败只记日志
     * <p>
     * 回填失败不能覆盖原始业务异常：调用方需要看到的是「业务为什么失败」，
     * 而不是「幂等 Token 为什么没还回去」。
     *
     * @param token        原 Token
     * @param username     当前用户
     * @param remainMillis 剩余有效期
     */
    private void restoreQuietly(String token, String username, long remainMillis) {
        try {
            idempotentTokenService.restore(token, username, remainMillis);
        } catch (Throwable ex) {
            log.error("回填幂等 Token 失败，username: {}", username, ex);
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
}
