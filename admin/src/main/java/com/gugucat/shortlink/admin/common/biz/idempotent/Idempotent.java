package com.gugucat.shortlink.admin.common.biz.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 一次性幂等 Token 校验
 * <p>
 * 标注在写接口上，请求必须携带请求头 {@code Idempotency-Token}，
 * 且该 Token 只能成功使用一次。适合「超时重试、双击、重复提交」会导致重复写入的接口。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
