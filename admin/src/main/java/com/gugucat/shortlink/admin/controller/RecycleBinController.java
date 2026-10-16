package com.gugucat.shortlink.admin.controller;

import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.gugucat.shortlink.admin.remote.dto.req.RecycleBinSaveReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站管理控制层
 * <p>
 * 回收站的读写都落在中台的 t_link 上，后管只做转发。
 */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 将短链接移入回收站
     */
    @PostMapping("/api/clearlink/admin/v0.1/recycle-bin/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam) {
        return shortLinkActualRemoteService.saveRecycleBin(requestParam);
    }
}
