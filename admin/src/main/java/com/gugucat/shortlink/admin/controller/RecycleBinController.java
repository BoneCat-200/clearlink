package com.gugucat.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.gugucat.shortlink.admin.remote.dto.req.RecycleBinRecoverReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.RecycleBinRemoveReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.RecycleBinSaveReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站管理控制层
 * <p>
 * 单条记录的写操作（移入 / 恢复 / 移除）后管只做转发；
 * 分页查询需要先按当前用户算出分组集合，所以走 RecycleBinService。
 */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 将短链接移入回收站
     */
    @PostMapping("/api/clearlink/admin/v0.1/recycle-bin/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam) {
        return shortLinkActualRemoteService.saveRecycleBin(requestParam);
    }

    /**
     * 分页查询回收站短链接
     */
    @GetMapping("/api/clearlink/admin/v0.1/recycle-bin/page")
    public Result<Page<ShortLinkPageRespDTO>> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return recycleBinService.pageRecycleBinShortLink(requestParam);
    }

    /**
     * 从回收站恢复短链接
     */
    @PostMapping("/api/clearlink/admin/v0.1/recycle-bin/recover")
    public Result<Void> recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO requestParam) {
        return shortLinkActualRemoteService.recoverRecycleBin(requestParam);
    }

    /**
     * 从回收站移除短链接
     */
    @PostMapping("/api/clearlink/admin/v0.1/recycle-bin/remove")
    public Result<Void> removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO requestParam) {
        return shortLinkActualRemoteService.removeRecycleBin(requestParam);
    }
}
