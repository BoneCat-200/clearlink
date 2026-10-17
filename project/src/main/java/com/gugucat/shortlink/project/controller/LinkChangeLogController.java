package com.gugucat.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.common.convention.result.Result;
import com.gugucat.shortlink.project.common.convention.result.Results;
import com.gugucat.shortlink.project.dto.req.ShortLinkChangeLogPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkChangeLogRespDTO;
import com.gugucat.shortlink.project.service.LinkChangeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接变更分组记录控制层
 */
@RestController
@RequiredArgsConstructor
public class LinkChangeLogController {

    private final LinkChangeLogService linkChangeLogService;

    /**
     * 分页查询某条短链接的变更分组历史
     */
    @GetMapping("/api/clearlink/v0.1/change-log")
    public Result<IPage<ShortLinkChangeLogRespDTO>> pageChangeLog(ShortLinkChangeLogPageReqDTO requestParam) {
        return Results.success(linkChangeLogService.pageChangeLog(requestParam));
    }
}
