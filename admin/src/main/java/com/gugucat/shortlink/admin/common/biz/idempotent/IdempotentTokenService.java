package com.gugucat.shortlink.admin.common.biz.idempotent;

import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.IDEMPOTENT_TOKEN_KEY;

/**
 * 一次性幂等 Token 的签发与消费
 * <p>
 * Token 存 Redis，值为签发时的用户名。消费必须在 Lua 里原子完成：
 * 「先 GET 再 DEL」分成两条命令时，两个并发请求可以同时 GET 成功，Token 就失去了「一次性」。
 */
@Service
@RequiredArgsConstructor
public class IdempotentTokenService {

    /**
     * 请求头名称
     */
    public static final String TOKEN_HEADER = "Idempotency-Token";

    /**
     * 消费脚本路径
     */
    private static final String CONSUME_LUA_PATH = "lua/idempotent_token_consume.lua";

    private final StringRedisTemplate stringRedisTemplate;
    private final IdempotentProperties idempotentProperties;

    private DefaultRedisScript<Long> consumeScript;

    @PostConstruct
    public void init() {
        consumeScript = new DefaultRedisScript<>();
        consumeScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(CONSUME_LUA_PATH)));
        consumeScript.setResultType(Long.class);
    }

    /**
     * 签发 Token
     *
     * @param username 当前登录用户
     * @return Token
     */
    public String issue(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                tokenKey(token),
                username,
                idempotentProperties.getTokenTtlMinutes(),
                TimeUnit.MINUTES);
        return token;
    }

    /**
     * 原子消费 Token
     *
     * @param token    请求携带的 Token
     * @param username 当前登录用户
     * @return 消费后 Token 剩余的毫秒数，用于业务失败时按原有效期回填
     * @throws ClientException Token 不存在、已过期或不属于当前用户
     */
    public long consume(String token, String username) {
        Long remainMillis = stringRedisTemplate.execute(
                consumeScript,
                List.of(tokenKey(token)),
                username);
        if (remainMillis == null || remainMillis < 0) {
            // 不区分「不存在」与「不属于你」，避免通过错误信息探测他人 Token 是否有效
            throw new ClientException(BaseErrorCode.IDEMPOTENT_TOKEN_DELETE_ERROR);
        }
        return remainMillis;
    }

    /**
     * 业务确定性失败时回填 Token
     * <p>
     * 只在「业务已回滚、确认没有副作用」时调用，例如参数校验失败。
     * 系统异常、远程调用结果不确定时不回填，否则重试可能造成重复写入。
     *
     * @param token        原 Token
     * @param username     当前登录用户
     * @param remainMillis 消费时剩余的毫秒数
     */
    public void restore(String token, String username, long remainMillis) {
        long ttlMillis = remainMillis > 0
                ? remainMillis
                : TimeUnit.MINUTES.toMillis(idempotentProperties.getTokenTtlMinutes());
        stringRedisTemplate.opsForValue().set(tokenKey(token), username, ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Token 在 Redis 中的键
     *
     * @param token Token
     * @return Redis key
     */
    private String tokenKey(String token) {
        return IDEMPOTENT_TOKEN_KEY + token;
    }
}
