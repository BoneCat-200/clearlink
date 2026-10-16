package com.gugucat.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkGroupStatsReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkStatsReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkStatsRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接监控控制层
 * <p>
 * 后管不查监控数据，只做转发：短链接与统计都在中台，后管手里只有用户态。
 * 用户态由 Feign 拦截器自动带上（见 {@code OpenFeignConfiguration}），
 * 所以这里只需要把查询参数原样转过去。
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 访问单个短链接在指定日期内的监控数据
     */
    @GetMapping("/api/clearlink/admin/v0.1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(ShortLinkStatsReqDTO requestParam) {
        return shortLinkActualRemoteService.oneShortLinkStats(
                requestParam.getFullShortUrl(),
                requestParam.getGid(),
                requestParam.getEnableStatus(),
                requestParam.getStartDate(),
                requestParam.getEndDate()
        );
    }

    /**
     * 访问指定分组在指定日期内的监控数据
     */
    @GetMapping("/api/clearlink/admin/v0.1/stats/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        return shortLinkActualRemoteService.groupShortLinkStats(
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate()
        );
    }

    /**
     * 访问单个短链接在指定日期内的访问记录
     */
    @GetMapping("/api/clearlink/admin/v0.1/stats/access-record")
    public Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        return shortLinkActualRemoteService.shortLinkStatsAccessRecord(
                requestParam.getFullShortUrl(),
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate(),
                requestParam.getEnableStatus(),
                requestParam.getCurrent(),
                requestParam.getSize()
        );
    }

    /**
     * 访问指定分组在指定日期内的访问记录
     */
    @GetMapping("/api/clearlink/admin/v0.1/stats/access-record/group")
    public Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        return shortLinkActualRemoteService.groupShortLinkStatsAccessRecord(
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate(),
                requestParam.getCurrent(),
                requestParam.getSize()
        );
    }
}
