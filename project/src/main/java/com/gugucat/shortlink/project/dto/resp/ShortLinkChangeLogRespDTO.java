package com.gugucat.shortlink.project.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接变更分组记录响应参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkChangeLogRespDTO {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 原分组标识
     */
    private String originGid;

    /**
     * 新分组标识
     */
    private String currentGid;

    /**
     * 操作人用户名
     */
    private String operator;

    /**
     * 变更时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
