package com.gugucat.shortlink.project.job;

import com.gugucat.shortlink.project.common.enums.ReconcileTriggerTypeEnum;
import com.gugucat.shortlink.project.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 统计对账定时任务
 * <p>
 * 默认每天 02:10 对账前一天的数据。选择这个时间点是为了避开访问高峰，
 * 同时给统计消息的重试留出时间；但仍然不是「绝对静止」的时点，
 * 迟到消息导致的差异需要在报告里结合执行时刻理解。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsReconcileJob {

    private final ReconcileService reconcileService;

    /**
     * 对账昨天
     */
    @Scheduled(cron = "${short-link.reconcile.cron:0 10 2 * * ?}")
    public void reconcileYesterday() {
        LocalDate bizDate = LocalDate.now().minusDays(1);
        try {
            reconcileService.reconcile(bizDate, ReconcileTriggerTypeEnum.SCHEDULE);
        } catch (Throwable ex) {
            // 定时任务不能把异常抛给调度线程，否则后续调度会受影响；报告已由 service 落库
            log.error("定时统计对账任务执行失败，bizDate: {}", bizDate, ex);
        }
    }
}
