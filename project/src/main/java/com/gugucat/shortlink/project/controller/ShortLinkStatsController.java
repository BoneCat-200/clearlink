package com.gugucat.shortlink.project.controller;

import com.gugucat.shortlink.project.common.convention.result.Result;
import com.gugucat.shortlink.project.common.convention.result.Results;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsRespDTO;
import com.gugucat.shortlink.project.service.ShortLinkStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接监控控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkStatsService shortLinkStatsService;

    /**
     * 访问单个短链接在指定日期内的监控数据
     */
    @GetMapping("/api/clearlink/v0.1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(ShortLinkStatsReqDTO requestParam) {
        return Results.success(shortLinkStatsService.oneShortLinkStats(requestParam));
    }
}
