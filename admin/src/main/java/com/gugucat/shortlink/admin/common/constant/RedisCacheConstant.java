package com.gugucat.shortlink.admin.common.constant;


/**
 * Redis缓存常量类
 */

public class RedisCacheConstant {
    public static final String LOCK_USER_REGISTER_KEY = "clearlink:lock_user-register"; // 用户注册锁
    public static final String USER_LOGIN_KEY = "clearlink:login:"; // 用户登录缓存
}
