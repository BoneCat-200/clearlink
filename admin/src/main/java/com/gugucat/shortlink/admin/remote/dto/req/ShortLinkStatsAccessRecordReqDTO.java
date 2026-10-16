package com.gugucat.shortlink.admin.remote.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短链接监控访问记录请求参数
 * <p>
 * 与中台的同名 DTO 是一份契约拷贝。继承 Page 是为了让 current / size 直接从查询串绑定进来，
 * 泛型参数用响应 DTO：后管不持有中台的实体类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShortLinkStatsAccessRecordReqDTO extends Page<ShortLinkStatsAccessRecordRespDTO> {

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
     */
    private Integer enableStatus;
}
