package com.gugucat.shortlink.admin.common.biz.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解
 * <p>
 * 标在管理端的写接口上，由 {@link OperationLogAspect} 统一采集
 * 操作人、参数、结果、耗时等信息并异步落库。
 * <p>
 * 只在 admin 入口标注：链接、回收站等写操作在 admin 侧转发，
 * 若在 project 侧再标一次，聚合部署时会出现 admin + project 双份记录。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 业务模块，例如「用户」「分组」「短链接」
     *
     * @return 模块名
     */
    String module();

    /**
     * 操作动作，例如「创建」「修改」「删除」
     *
     * @return 动作名
     */
    String action();
}
