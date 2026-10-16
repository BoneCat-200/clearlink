package com.gugucat.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.project.dao.entity.LinkAccessLogsDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分组短链接监控访问记录请求参数
 * <p>
 * 与单个短链接版本相比少了 `fullShortUrl` 与 `enableStatus`：
 * 统计对象是整个分组，且分组统计一律只算正常状态的短链接。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShortLinkGroupStatsAccessRecordReqDTO extends Page<LinkAccessLogsDO> {

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
}
