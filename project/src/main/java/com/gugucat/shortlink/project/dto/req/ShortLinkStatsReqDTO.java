package com.gugucat.shortlink.project.dto.req;

import lombok.Data;

/**
 * 短链接监控请求参数
 */
@Data
public class ShortLinkStatsReqDTO {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 开始日期
     */
    private String startDate;

    /**
     * 结束日期
     */
    private String endDate;

    /**
     * 启用标识 0：启用 1：未启用
     * <p>
     * 库里存的是数值，但所有监控查询都把它当成条件的一部分，
     * 所以「回收站里的短链接」与「正常短链接」的统计是两套数据。
     */
    private Integer enableStatus;
}
