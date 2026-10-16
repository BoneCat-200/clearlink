package com.gugucat.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import lombok.Data;

/**
 * 短链接分页请求参数
 * <p>
 * 继承 Page 是为了让 MyBatis-Plus 的分页拦截器能从参数里认出 IPage，
 * 从而改写 SQL 追加 LIMIT 并生成 COUNT 查询。
 */
@Data
public class ShortLinkPageReqDTO extends Page<ShortLinkDO> {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 排序标识：todayPv / todayUv / todayUip / totalPv / totalUv / totalUip，
     * 缺省按创建时间倒序
     */
    private String orderTag;
}
