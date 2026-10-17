package com.gugucat.shortlink.admin.remote.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkChangeLogRespDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短链接变更分组记录分页查询参数
 * <p>
 * 与中台的同名 DTO 是一份契约拷贝。继承 Page 让 current / size 直接绑定，
 * 泛型用响应 DTO（后管不持有中台的实体类）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShortLinkChangeLogPageReqDTO extends Page<ShortLinkChangeLogRespDTO> {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 分组标识，用于归属校验
     */
    private String gid;
}
