package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;

/**
 * 回收站接口层
 */
public interface RecycleBinService {

    /**
     * 分页查询回收站短链接
     *
     * @param requestParam 分页查询请求参数
     * @return 回收站短链接分页结果
     */
    Result<Page<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
