package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkPageReqDTO;

/**
 * 短链接持久层
 */
public interface ShortLinkMapper extends BaseMapper<ShortLinkDO> {

    /**
     * 分页查询短链接
     *
     * @param requestParam 分页查询参数（含 gid、orderTag 与分页信息）
     * @return 短链接分页结果
     */
    IPage<ShortLinkDO> pageLink(ShortLinkPageReqDTO requestParam);
}
