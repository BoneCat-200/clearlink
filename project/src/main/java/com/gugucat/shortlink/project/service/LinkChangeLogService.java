package com.gugucat.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.dto.req.ShortLinkChangeLogPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkChangeLogRespDTO;

/**
 * 短链接变更分组记录接口层
 */
public interface LinkChangeLogService {

    /**
     * 分页查询某条短链接的变更分组历史
     *
     * @param requestParam 查询参数（含用于归属校验的 gid）
     * @return 按变更时间倒序的记录分页
     */
    IPage<ShortLinkChangeLogRespDTO> pageChangeLog(ShortLinkChangeLogPageReqDTO requestParam);
}
