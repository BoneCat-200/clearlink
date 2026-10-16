package com.gugucat.shortlink.project.service;

import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsRespDTO;

/**
 * 短链接监控接口层
 */
public interface ShortLinkStatsService {

    /**
     * 获取单个短链接在指定日期内的监控数据
     *
     * @param requestParam 监控查询参数
     * @return 监控数据；区间内没有任何访问时返回 null
     */
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);
}
