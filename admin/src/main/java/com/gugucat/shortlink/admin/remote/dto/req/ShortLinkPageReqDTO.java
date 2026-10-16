package com.gugucat.shortlink.admin.remote.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import lombok.Data;

/**
 * 短链接分页请求参数（后管侧）
 * <p>
 * 继承 Page 只为拿到 current / size 的默认值（1 / 10），
 * 真正的分页查询发生在中台，后管只是把这三个参数透传过去。
 */
@Data
public class ShortLinkPageReqDTO extends Page<ShortLinkPageRespDTO> {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 排序标识：todayPv / todayUv / todayUip / totalPv / totalUv / totalUip，
     * 缺省按创建时间倒序
     */
    private String orderTag;
}
