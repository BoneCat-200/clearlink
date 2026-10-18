package com.gugucat.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.common.enums.ReconcileTriggerTypeEnum;
import com.gugucat.shortlink.project.dto.req.ReconcileReportPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ReconcileReportRespDTO;

import java.time.LocalDate;

/**
 * 统计对账业务层
 */
public interface ReconcileService {

    /**
     * 对指定业务日期执行一次统计对账
     *
     * @param bizDate     业务日期；为空时取昨天
     * @param triggerType 触发方式
     * @return 本次对账报告
     */
    ReconcileReportRespDTO reconcile(LocalDate bizDate, ReconcileTriggerTypeEnum triggerType);

    /**
     * 分页查询对账报告
     *
     * @param requestParam 分页与过滤条件
     * @return 报告分页
     */
    IPage<ReconcileReportRespDTO> pageReconcileReport(ReconcileReportPageReqDTO requestParam);
}
