package com.gugucat.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.biz.audit.OperationLog;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkBatchCreateReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkBatchCreateRespDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接后管控制层
 * <p>
 * 后管自身不存短链接数据，全部转发给短链接中台（project 模块）。
 */
@RestController("shortLinkControllerByAdmin")  // 显式命名，避免聚合部署时与 project 模块的同名 Bean 冲突
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 创建短链接
     */
    @OperationLog(module = "短链接", action = "创建")
    @PostMapping("/api/clearlink/admin/v0.1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkActualRemoteService.createShortLink(requestParam);
    }

    /**
     * 批量创建短链接
     */
    @OperationLog(module = "短链接", action = "批量创建")
    @PostMapping("/api/clearlink/admin/v0.1/create/batch")
    public Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam) {
        return shortLinkActualRemoteService.batchCreateShortLink(requestParam);
    }

    /**
     * 分页查询短链接
     */
    @GetMapping("/api/clearlink/admin/v0.1/page")
    public Result<Page<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return shortLinkActualRemoteService.pageShortLink(
                requestParam.getGid(), requestParam.getOrderTag(), requestParam.getCurrent(), requestParam.getSize());
    }

    /**
     * 修改短链接
     */
    @OperationLog(module = "短链接", action = "修改")
    @PostMapping("/api/clearlink/admin/v0.1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        return shortLinkActualRemoteService.updateShortLink(requestParam);
    }
}
