package com.gugucat.shortlink.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gugucat.shortlink.gateway.config.TokenValidateFilterConfig;
import com.gugucat.shortlink.gateway.dto.GatewayErrorResult;
import com.gugucat.shortlink.gateway.util.TraceIdUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关 Token 校验过滤器
 * <p>
 * 统一在入口处校验登录态，校验通过后把用户信息补成请求头传给下游服务：
 * 下游（admin / project）不再信任客户端自带的 userId、realName，
 * 而是直接使用网关写入的值 —— 否则调用方伪造一个 userId 头就能越权。
 * <p>
 * 校验依据与 admin 登录时写入的是同一份数据：
 * Redis Hash {@code clearlink:login:{username}} 的 field 为 token，value 为用户 JSON。
 */
@Component
public class TokenValidateGatewayFilterFactory extends AbstractGatewayFilterFactory<TokenValidateFilterConfig> {

    /**
     * 登录态在 Redis 中的 key 前缀，必须与 admin 模块的 RedisCacheConstant.USER_LOGIN_KEY 一致
     */
    private static final String USER_LOGIN_KEY = "clearlink:login:";

    /**
     * 注册接口：POST 且路径精确匹配时才放行
     */
    private static final String USER_REGISTER_PATH = "/api/clearlink/admin/v0.1/user";

    private final StringRedisTemplate stringRedisTemplate;

    public TokenValidateGatewayFilterFactory(StringRedisTemplate stringRedisTemplate) {
        super(TokenValidateFilterConfig.class);
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public GatewayFilter apply(TokenValidateFilterConfig config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestPath = request.getPath().toString();
            String requestMethod = request.getMethod().name();
            if (isPathInWhiteList(requestPath, requestMethod, config.getWhitePathList())) {
                return chain.filter(exchange);
            }
            String username = request.getHeaders().getFirst("username");
            String token = request.getHeaders().getFirst("token");
            String traceId = request.getHeaders().getFirst(TraceIdUtils.TRACE_ID_HEADER);
            if (!StringUtils.hasText(username) || !StringUtils.hasText(token)) {
                return unauthorized(exchange.getResponse(), "请先登录", traceId);
            }
            Object userInfoJsonStr = stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token);
            if (userInfoJsonStr == null) {
                return unauthorized(exchange.getResponse(), "登录已过期，请重新登录", traceId);
            }
            JSONObject userInfo = JSON.parseObject(userInfoJsonStr.toString());
            // 下游用的是 username/userId/realName 三个头：
            // userId、realName 由网关按登录态写入，覆盖客户端传来的同名头
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.set("userId", userInfo.getString("id"));
                        // HTTP 头按 ISO-8859-1 传输，中文真实姓名必须编码，否则会被容器拒绝
                        headers.set("realName", URLEncoder.encode(
                                userInfo.getString("realName") == null ? "" : userInfo.getString("realName"),
                                StandardCharsets.UTF_8));
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    /**
     * 判断当前请求是否无需登录
     * <p>
     * 白名单用精确匹配而不是前缀匹配：前缀匹配会把
     * {@code /user/login/check} 这类「名字以 login 开头」的接口一起放行。
     *
     * @param requestPath   请求路径
     * @param requestMethod 请求方法
     * @param whitePathList 白名单
     * @return 是否放行
     */
    private boolean isPathInWhiteList(String requestPath, String requestMethod, List<String> whitePathList) {
        if (!CollectionUtils.isEmpty(whitePathList) && whitePathList.contains(requestPath)) {
            return true;
        }
        // 用户注册：只有 POST 到该路径才放行，GET/PUT/DELETE 仍需要登录
        return "POST".equals(requestMethod) && USER_REGISTER_PATH.equals(requestPath);
    }

    /**
     * 返回 401 与统一 JSON
     *
     * @param response 响应
     * @param message  错误信息
     * @param traceId  请求 ID
     * @return 写出响应的 Mono
     */
    private Mono<Void> unauthorized(ServerHttpResponse response, String message, String traceId) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.fromSupplier(() -> {
            DataBufferFactory bufferFactory = response.bufferFactory();
            GatewayErrorResult result = GatewayErrorResult.builder()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .message(message)
                    .requestId(traceId)
                    .build();
            return bufferFactory.wrap(JSON.toJSONString(result).getBytes(StandardCharsets.UTF_8));
        }));
    }
}
