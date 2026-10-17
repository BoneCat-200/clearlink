package com.gugucat.shortlink.project.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接基础信息响应参数
 * <p>
 * 批量创建里每条成功记录只回这三个字段：调用方要做的是把「原链接 ↔ 短链接」建立映射，
 * 其余信息（pv/uv、有效期、图标等）单条查询就能拿到。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkBaseInfoRespDTO {

    /**
     * 描述信息
     */
    private String describe;

    /**
     * 原始链接
     */
    private String originUrl;

    /**
     * 短链接
     */
    private String fullShortUrl;
}
