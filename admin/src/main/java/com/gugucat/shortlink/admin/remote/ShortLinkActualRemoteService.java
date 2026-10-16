package com.gugucat.shortlink.admin.remote;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 短链接中台远程调用服务
 * <p>
 * 只给服务名不给 url，走 Nacos 服务发现找 clearlink-project 的实例。
 */
@FeignClient(value = "clearlink-project")
public interface ShortLinkActualRemoteService {

    /**
     * 创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建响应
     */
    @PostMapping("/api/clearlink/v0.1/create")
    Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam);

    /**
     * 分页查询短链接
     *
     * @param gid      分组标识
     * @param orderTag 排序类型
     * @param current  当前页
     * @param size     每页条数
     * @return 查询短链接响应
     */
    @GetMapping("/api/clearlink/v0.1/page")
    Result<Page<ShortLinkPageRespDTO>> pageShortLink(@RequestParam("gid") String gid,
                                                     @RequestParam("orderTag") String orderTag,
                                                     @RequestParam("current") Long current,
                                                     @RequestParam("size") Long size);
}
