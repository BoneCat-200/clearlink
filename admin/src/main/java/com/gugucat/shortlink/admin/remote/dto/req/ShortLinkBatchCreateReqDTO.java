package com.gugucat.shortlink.admin.remote.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 短链接批量创建请求对象
 * <p>
 * 与中台的同名 DTO 是一份契约拷贝。`originUrls` 与 `describes` 是按下标对应的
 * 两个平行集合，长度必须一致。
 */
@Data
public class ShortLinkBatchCreateReqDTO {

    /**
     * 原始链接集合
     */
    private List<String> originUrls;

    /**
     * 描述集合，可为空
     */
    private List<String> describes;

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 创建类型 0：接口创建 1：控制台创建
     */
    private Integer createdType;

    /**
     * 有效期类型 0：永久有效 1：自定义
     */
    private Integer validDateType;

    /**
     * 有效期
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /**
     * 域名，为空则用默认域名（必须在白名单内）
     */
    private String domain;
}
