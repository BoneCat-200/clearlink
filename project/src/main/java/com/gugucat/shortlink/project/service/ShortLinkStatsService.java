package com.gugucat.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsAccessRecordRespDTO;
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

    /**
     * 获取指定分组在指定日期内的监控数据
     * <p>
     * 返回体与单链接版本是同一个结构，但**不含 `uvTypeStats`**：
     * 分组维度的新老访客要按「访客在整个分组上的全部历史」判断，
     * 口径与单链接不同，参考实现也没有实现。
     *
     * @param requestParam 分组监控查询参数
     * @return 监控数据；区间内没有任何访问时返回 null
     */
    ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam);

    /**
     * 分页查询单个短链接的访问记录
     *
     * @param requestParam 访问记录查询参数
     * @return 访问记录分页，按访问时间倒序
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam);

    /**
     * 分页查询指定分组的访问记录
     *
     * @param requestParam 分组访问记录查询参数
     * @return 访问记录分页，按访问时间倒序
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(ShortLinkGroupStatsAccessRecordReqDTO requestParam);
}
