package com.gugucat.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.common.convention.result.Result;
import com.gugucat.shortlink.project.common.convention.result.Results;
import com.gugucat.shortlink.project.common.enums.ReconcileTriggerTypeEnum;
import com.gugucat.shortlink.project.dto.req.ReconcileReportPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ReconcileReportRespDTO;
import com.gugucat.shortlink.project.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 统计对账控制层
 */
@RestController
@RequiredArgsConstructor
public class ReconcileController {

    private final ReconcileService reconcileService;

    /**
     * 手动触发一次对账
     * <p>
     * 不传 bizDate 时对账昨天；同一业务日期同时只允许一个任务执行。
     */
    @PostMapping("/api/clearlink/v0.1/reconcile/run")
    public Result<ReconcileReportRespDTO> run(
            @RequestParam(value = "bizDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        return Results.success(reconcileService.reconcile(bizDate, ReconcileTriggerTypeEnum.MANUAL));
    }

    /**
     * 分页查询对账报告
     */
    @GetMapping("/api/clearlink/v0.1/reconcile/page")
    public Result<IPage<ReconcileReportRespDTO>> page(ReconcileReportPageReqDTO requestParam) {
        return Results.success(reconcileService.pageReconcileReport(requestParam));
    }
}
