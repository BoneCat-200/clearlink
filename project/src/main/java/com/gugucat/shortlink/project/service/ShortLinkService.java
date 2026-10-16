package com.gugucat.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import com.gugucat.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/**
 * 短链接接口层
 */
public interface ShortLinkService {

    /**
     * 创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建信息
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * 分页查询短链接
     *
     * @param requestParam 分页查询请求参数
     * @return 短链接分页结果
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    /**
     * 修改短链接
     *
     * @param requestParam 修改短链接请求参数
     */
    void updateShortLink(ShortLinkUpdateReqDTO requestParam);

    /**
     * 短链接跳转
     *
     * @param shortUri 短链接后缀
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    void restoreUrl(String shortUri, ServletRequest request, ServletResponse response);

    /**
     * 短链接访问统计
     *
     * @param statsRecord 本次访问的统计信息
     */
    void shortLinkStats(ShortLinkStatsRecordDTO statsRecord);
}
