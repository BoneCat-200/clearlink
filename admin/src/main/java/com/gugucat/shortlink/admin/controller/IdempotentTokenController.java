package com.gugucat.shortlink.admin.controller;

import cn.hutool.core.util.StrUtil;
import com.gugucat.shortlink.admin.common.biz.idempotent.IdempotentTokenService;
import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.dto.resp.IdempotentTokenRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一次性幂等 Token 控制层
 */
@RestController
@RequiredArgsConstructor
public class IdempotentTokenController {

    private final IdempotentTokenService idempotentTokenService;

    /**
     * 签发一次性幂等 Token
     * <p>
     * 调用写接口前先取 Token，再把 Token 放进 {@code Idempotency-Token} 请求头。
     * Token 与当前登录用户绑定，不能转给他人使用。
     */
    @GetMapping("/api/clearlink/admin/v0.1/idempotent/token")
    public Result<IdempotentTokenRespDTO> issueToken() {
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            throw new ClientException("用户未登录");
        }
        return Results.success(new IdempotentTokenRespDTO(idempotentTokenService.issue(username)));
    }
}
