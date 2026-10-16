package com.gugucat.shortlink.admin.remote;

import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
}
