package com.gugucat.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.project.dao.entity.LinkAccessLogsDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短链接监控访问记录请求参数
 * <p>
 * 继承 MyBatis-Plus 的 {@link Page}，因此分页参数（current / size）直接来自查询串，
 * 分页由分页拦截器完成，不需要自己拼 LIMIT。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShortLinkStatsAccessRecordReqDTO extends Page<LinkAccessLogsDO> {

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
