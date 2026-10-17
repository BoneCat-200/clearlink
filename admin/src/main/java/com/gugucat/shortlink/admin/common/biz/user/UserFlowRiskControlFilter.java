package com.gugucat.shortlink.admin.common.biz.user;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.config.UserFlowRiskControlConfiguration;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.USER_FLOW_RISK_CONTROL_KEY;

/**
 * 用户操作流量风控过滤器
 * <p>
 * 在固定时间窗口内对请求计数，超过阈值直接返回「当前系统繁忙」，不进入业务。
 * 计数用 Lua 脚本完成，保证 INCR 与设置过期时间是原子的 ——
 * 否则第一次请求还没执行到 EXPIRE 时进程挂掉，这个计数键就永远不过期，
 * 该用户会被永久限流。
 */
@Slf4j
@RequiredArgsConstructor
public class UserFlowRiskControlFilter implements Filter {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserFlowRiskControlConfiguration userFlowRiskControlConfiguration;

    /**
     * 计数器脚本路径
     */
    private static final String USER_FLOW_RISK_CONTROL_LUA_SCRIPT_PATH = "lua/user_flow_risk_control.lua";

    /**
     * 计数桶前缀：已登录按用户、未登录按 IP
     */
    private static final String USER_BUCKET_PREFIX = "user:";

    /**
     * 计数桶前缀：未登录按 IP
     */
    private static final String IP_BUCKET_PREFIX = "ip:";

    @SneakyThrows
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(USER_FLOW_RISK_CONTROL_LUA_SCRIPT_PATH)));
        redisScript.setResultType(Long.class);
        String accessKey = USER_FLOW_RISK_CONTROL_KEY + resolveBucket(request);
        Long currentAccessCount;
        try {
            currentAccessCount = stringRedisTemplate.execute(
                    redisScript,
                    List.of(accessKey),
                    userFlowRiskControlConfiguration.getTimeWindow());
        } catch (Throwable ex) {
            // 风控依赖 Redis。脚本执行失败时选择「拒绝」而不是「放行」：
            // 风控的作用就是保护后端，Redis 不可用时如果放行，等于风控在最需要它的时候失效。
            // 代价是 Redis 抖动期间管理端不可用 —— 这是有意的取舍
            log.error("执行用户请求流量限制 LUA 脚本出错", ex);
            returnJson((HttpServletResponse) response);
            return;
        }
        if (currentAccessCount == null || currentAccessCount > userFlowRiskControlConfiguration.getMaxAccessCount()) {
            returnJson((HttpServletResponse) response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 决定这次请求计入哪个桶
     * <p>
     * 已登录按用户名；未登录按客户端 IP。
     * <p>
     * 参考实现把所有未登录请求都归到同一个 {@code "other"} 桶里 ——
     * 那意味着**任何一个匿名调用方都能把桶刷满，进而把所有未登录请求一起挡掉**。
     * 按 IP 分桶后，匿名流量之间互不影响。
     *
     * @param request 请求
     * @return 计数桶标识
     */
    private String resolveBucket(ServletRequest request) {
        String username = UserContext.getUsername();
        if (StrUtil.isNotBlank(username)) {
            return USER_BUCKET_PREFIX + username;
        }
        return IP_BUCKET_PREFIX + getClientIp((HttpServletRequest) request);
    }

    /**
     * 取客户端 IP，用于未登录请求分桶
     * <p>
     * 优先取代理头：经过网关/Nginx 之后 {@code getRemoteAddr()} 拿到的是代理地址。
     * {@code X-Forwarded-For} 可能是「客户端, 代理1, 代理2」的列表，取第一个。
     *
     * @param request 请求
     * @return 客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return StrUtil.splitTrim(ip, ',').get(0);
        }
        return StrUtil.blankToDefault(request.getRemoteAddr(), "unknown");
    }

    /**
     * 返回限流响应
     * <p>
     * 用 {@code application/json} 而不是参考实现的 {@code text/html}：响应体本来就是 JSON，
     * 声明成 html 会让调用方按错误的方式解析。
     *
     * @param response HTTP 响应
     * @throws IOException 写出响应失败
     */
    private void returnJson(HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.print(JSON.toJSONString(Results.failure(new ClientException(BaseErrorCode.FLOW_LIMIT_ERROR))));
        }
    }
}
