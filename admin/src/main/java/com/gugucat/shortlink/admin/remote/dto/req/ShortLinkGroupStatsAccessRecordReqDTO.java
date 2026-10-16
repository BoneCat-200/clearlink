package com.gugucat.shortlink.admin.remote.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分组短链接监控访问记录请求参数
 * <p>
 * 比单个短链接版本少 `fullShortUrl` 与 `enableStatus`：
 * 统计对象是整个分组，且分组统计一律只算正常状态的短链接。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShortLinkGroupStatsAccessRecordReqDTO extends Page<ShortLinkStatsAccessRecordRespDTO> {

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
