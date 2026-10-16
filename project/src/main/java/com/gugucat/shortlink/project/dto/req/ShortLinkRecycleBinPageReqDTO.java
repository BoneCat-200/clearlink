package com.gugucat.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import lombok.Data;

import java.util.List;

/**
 * 回收站短链接分页请求参数
 * <p>
 * 与短链接分页不同，这里的查询范围是「当前用户的**所有**分组」——
 * 回收站是跨分组的视图，所以条件从单个 gid 变成一个 gidList。
 * <p>
 * 继承 Page 是为了让 MyBatis-Plus 的分页拦截器能从参数里认出 IPage。
 */
@Data
public class ShortLinkRecycleBinPageReqDTO extends Page<ShortLinkDO> {

    /**
     * 分组标识集合
     */
    private List<String> gidList;
}
