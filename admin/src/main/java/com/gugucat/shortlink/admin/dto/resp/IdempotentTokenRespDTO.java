package com.gugucat.shortlink.admin.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次性幂等 Token 响应实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotentTokenRespDTO {

    /**
     * 幂等 Token，写请求时放入 Idempotency-Token 请求头
     */
    private String token;
}
