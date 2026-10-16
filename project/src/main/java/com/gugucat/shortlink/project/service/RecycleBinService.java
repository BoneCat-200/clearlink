package com.gugucat.shortlink.project.service;

import com.gugucat.shortlink.project.dto.req.RecycleBinSaveReqDTO;

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
}
