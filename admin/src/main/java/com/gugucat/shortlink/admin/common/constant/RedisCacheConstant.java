package com.gugucat.shortlink.admin.common.constant;


/**
 * Redis缓存常量类
 */

public class RedisCacheConstant {
    public static final String LOCK_USER_REGISTER_KEY = "clearlink:lock_user-register"; // 用户注册锁
    public static final String USER_LOGIN_KEY = "clearlink:login:"; // 用户登录缓存

    /**
     * 登录失败计数键前缀，后面接用户名
     */
    public static final String USER_LOGIN_FAIL_KEY = "clearlink:login:fail:";

    /**
     * 登录锁定键前缀，后面接用户名
     */
    public static final String USER_LOGIN_LOCK_KEY = "clearlink:login:lock:";

    /**
     * 一次性幂等 Token 键前缀，后面接 Token
     */
    public static final String IDEMPOTENT_TOKEN_KEY = "clearlink:idempotent:token:";

    /**
     * 创建分组锁，按用户名加锁
     * <p>
     * 「先查数量、再插入」不是原子的：两个请求同时看到「还差一个到上限」，
     * 就会各插一条，一起突破上限。按用户加锁把这一步串起来。
     */
    public static final String LOCK_GROUP_CREATE_KEY = "clearlink:lock:group:create:%s";

    /**
     * 用户流量风控计数键前缀，后面接计数桶（已登录按用户、未登录按 IP）
     */
    public static final String USER_FLOW_RISK_CONTROL_KEY = "clearlink:user-flow-risk-control:";
}
