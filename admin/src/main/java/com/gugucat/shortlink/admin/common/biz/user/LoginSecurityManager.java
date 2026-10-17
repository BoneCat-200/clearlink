package com.gugucat.shortlink.admin.common.biz.user;

import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_FAIL_KEY;
import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_LOCK_KEY;

/**
 * 登录失败锁定
 * <p>
 * 规则：统计窗口内连续失败达到阈值后锁定账号一段时间，锁定期间即使密码正确也拒绝登录。
 * <p>
 * 只对**真实存在**的用户计数：用户名不存在时不计数，
 * 避免攻击者用不存在的用户名刷计数键，也避免误锁一个根本不存在的账号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSecurityManager {

    /**
     * 失败计数脚本路径
     */
    private static final String LOGIN_FAIL_COUNT_LUA_PATH = "lua/login_fail_count.lua";

    private final StringRedisTemplate stringRedisTemplate;
    private final LoginSecurityProperties properties;

    /**
     * 脚本在容器启动时加载一次，避免每次登录失败都读一遍 classpath 资源
     */
    private DefaultRedisScript<Long> failCountScript;

    @PostConstruct
    public void init() {
        failCountScript = new DefaultRedisScript<>();
        failCountScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(LOGIN_FAIL_COUNT_LUA_PATH)));
        failCountScript.setResultType(Long.class);
    }

    /**
     * 登录前检查账号是否处于锁定状态
     *
     * @param username 用户名
     * @throws ClientException 账号处于锁定期
     */
    public void checkNotLocked(String username) {
        String lockKey = USER_LOGIN_LOCK_KEY + username;
        Long ttlSeconds = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        if (ttlSeconds != null && ttlSeconds > 0) {
            // 不足一分钟也按一分钟提示，避免出现「请 0 分钟后重试」
            long minutes = Math.max(1, (ttlSeconds + 59) / 60);
            throw new ClientException("账号已锁定，请 " + minutes + " 分钟后重试",
                    BaseErrorCode.LOGIN_LOCKED_ERROR);
        }
    }

    /**
     * 记录一次登录失败
     * <p>
     * 达到阈值时立即建立锁定键，并把失败计数清掉：
     * 锁定期间不再累加，锁过期后从 0 重新开始。
     *
     * @param username 用户名
     * @throws ClientException 本次失败已达到阈值、账号开始锁定
     */
    public void recordFailure(String username) {
        Long failCount = stringRedisTemplate.execute(
                failCountScript,
                List.of(USER_LOGIN_FAIL_KEY + username),
                String.valueOf(TimeUnit.MINUTES.toSeconds(properties.getFailWindowMinutes())));
        if (failCount != null && failCount >= properties.getMaxFailCount()) {
            stringRedisTemplate.opsForValue().set(
                    USER_LOGIN_LOCK_KEY + username,
                    "1",
                    properties.getLockMinutes(),
                    TimeUnit.MINUTES);
            stringRedisTemplate.delete(USER_LOGIN_FAIL_KEY + username);
            log.warn("账号连续登录失败达到阈值，已锁定 username: {}, lockMinutes: {}",
                    username, properties.getLockMinutes());
            throw new ClientException("账号已锁定，请 " + properties.getLockMinutes() + " 分钟后重试",
                    BaseErrorCode.LOGIN_LOCKED_ERROR);
        }
    }

    /**
     * 登录成功后清除失败计数与锁定标记
     *
     * @param username 用户名
     */
    public void clear(String username) {
        stringRedisTemplate.delete(List.of(
                USER_LOGIN_FAIL_KEY + username,
                USER_LOGIN_LOCK_KEY + username));
    }
}
