package com.gugucat.shortlink.project.common.constant;

/**
 * Redis Key 常量类
 * <p>
 * 只登记当前功能真正用到的 Key，后续功能用到再往下追加。
 */
public class RedisKeyConstant {

    /**
     * 短链接跳转前缀 Key
     */
    public static final String GOTO_SHORT_LINK_KEY = "clearlink:goto:%s";

    /**
     * 短链接修改分组 ID 锁前缀 Key
     */
    public static final String LOCK_GID_UPDATE_KEY = "clearlink:lock:update-gid:%s";
}
