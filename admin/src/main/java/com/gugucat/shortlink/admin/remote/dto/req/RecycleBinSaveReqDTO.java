package com.gugucat.shortlink.admin.remote.dto.req;

import lombok.Data;

/**
 * 回收站保存请求对象（与中台 project 模块的契约拷贝）
 */
@Data
public class RecycleBinSaveReqDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 完整短链接
     */
    private String fullShortUrl;
}
