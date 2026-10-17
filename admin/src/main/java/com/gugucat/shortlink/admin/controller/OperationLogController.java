package com.gugucat.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.dto.req.OperationLogPageReqDTO;
import com.gugucat.shortlink.admin.dto.resp.OperationLogRespDTO;
import com.gugucat.shortlink.admin.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计日志控制层
 * <p>
 * 只提供查询，审计写入由切面异步完成。
 * 查询范围固定为当前登录用户，不支持按 username 查他人记录。
 */
@RestController("operationLogControllerByAdmin")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询当前用户的操作审计日志
     */
    @GetMapping("/api/clearlink/admin/v0.1/operation-log/page")
    public Result<IPage<OperationLogRespDTO>> pageOperationLog(OperationLogPageReqDTO requestParam) {
        return Results.success(operationLogService.pageOperationLog(requestParam));
    }
}
