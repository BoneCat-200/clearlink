package com.gugucat.shortlink.admin.remote.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import lombok.Data;

import java.util.List;

/**
 * 回收站短链接分页请求参数（后管侧）
 * <p>
 * 继承 Page 只为拿到 current / size 的默认值（1 / 10），真正的分页查询发生在中台。
 */
@Data
public class ShortLinkRecycleBinPageReqDTO extends Page<ShortLinkPageRespDTO> {

    /**
     * 分组标识集合
     * <p>
     * 《接口文档.md》把它列为请求参数，但**服务端一律用当前登录用户自己的分组覆盖它**：
     * 否则任何登录用户只要传别人的 gid，就能看到别人的回收站。
     */
    private List<String> gidList;
}
