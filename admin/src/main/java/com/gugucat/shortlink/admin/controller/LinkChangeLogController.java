package com.gugucat.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkChangeLogPageReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkChangeLogRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接变更分组记录控制层
 * <p>
 * 换组动作发生在中台（跨分组迁移要读写 t_link/t_link_goto），变更记录也在中台。
 * 后管只做转发，用户态由 Feign 拦截器透传（中台要按 gid + username 校验归属）。
 */
@RestController
@RequiredArgsConstructor
public class LinkChangeLogController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 分页查询某条短链接的变更分组历史
     */
    @GetMapping("/api/clearlink/admin/v0.1/change-log")
    public Result<Page<ShortLinkChangeLogRespDTO>> pageChangeLog(ShortLinkChangeLogPageReqDTO requestParam) {
        return shortLinkActualRemoteService.pageChangeLog(
                requestParam.getFullShortUrl(),
                requestParam.getGid(),
                requestParam.getCurrent(),
                requestParam.getSize()
        );
    }
}
