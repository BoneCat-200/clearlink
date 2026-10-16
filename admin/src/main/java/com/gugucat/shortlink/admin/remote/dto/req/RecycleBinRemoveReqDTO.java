package com.gugucat.shortlink.admin.remote.dto.req;

import lombok.Data;

/**
 * 回收站移除请求对象（与中台 project 模块的契约拷贝）
 */
@Data
public class RecycleBinRemoveReqDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 完整短链接
     */
    private String fullShortUrl;
}
