package com.gugucat.shortlink.admin.common.biz.audit;

import com.gugucat.shortlink.admin.dao.entity.OperationLogDO;
import com.gugucat.shortlink.admin.dao.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作审计日志异步写入器
 * <p>
 * 独立 Bean 是为了让 {@code @Async} 经过 Spring 代理；
 * 若写成切面里的私有方法，异步注解不会生效，审计写入会拖慢业务请求。
 * <p>
 * 写入失败只记日志：审计是旁路，不能反过来影响业务成功与否。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogRecorder {

    private final OperationLogMapper operationLogMapper;

    /**
     * 异步写入一条审计日志
     *
     * @param operationLogDO 审计日志
     */
    @Async("operationLogExecutor")
    public void record(OperationLogDO operationLogDO) {
        try {
            operationLogMapper.insert(operationLogDO);
        } catch (Throwable ex) {
            log.error("写入操作审计日志失败，不影响业务。module: {}, action: {}, traceId: {}",
                    operationLogDO.getModule(), operationLogDO.getAction(), operationLogDO.getTraceId(), ex);
        }
    }
}
