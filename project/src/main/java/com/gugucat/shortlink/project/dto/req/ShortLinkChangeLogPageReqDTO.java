package com.gugucat.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.project.dao.entity.LinkChangeLogDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短链接变更分组记录分页查询参数
 * <p>
 * 继承 Page，分页参数（current / size）直接从查询串绑定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShortLinkChangeLogPageReqDTO extends Page<LinkChangeLogDO> {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 分组标识
     * <p>
     * 用途是**归属校验**（这个分组是不是当前用户的），不是查询条件 ——
     * 变更历史是「这条短链接」维度的，与它现在在哪个分组无关。
     */
    private String gid;
}
