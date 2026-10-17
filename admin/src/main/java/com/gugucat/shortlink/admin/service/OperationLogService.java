package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.admin.dto.req.OperationLogPageReqDTO;
import com.gugucat.shortlink.admin.dto.resp.OperationLogRespDTO;

/**
 * 操作审计日志接口层
 */
public interface OperationLogService {

    /**
     * 分页查询当前用户的操作审计日志
     *
     * @param requestParam 查询参数
     * @return 分页结果
     */
    IPage<OperationLogRespDTO> pageOperationLog(OperationLogPageReqDTO requestParam);
}
