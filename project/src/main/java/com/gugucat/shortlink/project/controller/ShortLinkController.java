package com.gugucat.shortlink.project.controller;

import com.gugucat.shortlink.project.common.convention.result.Result;
import com.gugucat.shortlink.project.common.convention.result.Results;
import com.gugucat.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.project.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    /**
     * 创建短链接
     */
    @PostMapping("/api/clearlink/v0.1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortLinkService.createShortLink(requestParam));
    }
}
