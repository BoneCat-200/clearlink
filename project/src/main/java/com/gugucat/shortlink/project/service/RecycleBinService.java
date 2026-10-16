package com.gugucat.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.dto.req.RecycleBinRecoverReqDTO;
import com.gugucat.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/**
 * 回收站管理接口层
 */
public interface RecycleBinService {

    /**
     * 将短链接移入回收站
     *
     * @param requestParam 移入回收站请求参数
     */
    void saveRecycleBin(RecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站短链接
     *
     * @param requestParam 分页查询请求参数
     * @return 回收站短链接分页结果
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam);

    /**
     * 从回收站恢复短链接
     *
     * @param requestParam 恢复短链接请求参数
     */
    void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam);
}
