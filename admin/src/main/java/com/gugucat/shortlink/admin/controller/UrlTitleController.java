package com.gugucat.shortlink.admin.controller;

import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.ShortLinkActualRemoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * URL 标题控制层
 * <p>
 * 抓取动作在中台完成，后管只做转发。
 */
@RestController("urlTitleControllerByAdmin")  // 显式命名，避免聚合部署时与 project 模块的同名 Bean 冲突
@RequiredArgsConstructor
public class UrlTitleController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 根据 URL 获取对应网站的标题
     */
    @GetMapping("/api/clearlink/admin/v0.1/title")
    public Result<String> getTitleByUrl(@RequestParam("url") String url) {
        return shortLinkActualRemoteService.getTitleByUrl(url);
    }
}
