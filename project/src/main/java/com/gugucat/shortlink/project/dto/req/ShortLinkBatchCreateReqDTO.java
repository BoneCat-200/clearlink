package com.gugucat.shortlink.project.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 短链接批量创建请求对象
 * <p>
 * 与单条创建的差别：原始链接和描述是**两个平行的集合**，按下标一一对应。
 * 因此两者的长度必须一致，否则第 i 条会取到别人的描述（或直接下标越界）。
 */
@Data
public class ShortLinkBatchCreateReqDTO {

    /**
     * 原始链接集合
     */
    private List<String> originUrls;

    /**
     * 描述集合，可为空（为空时所有短链接都没有描述）
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
     * <p>
     * 参考实现的批量创建没有这个字段（那时单条创建也还没支持指定域名）。
     */
    private String domain;
}
