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
     * 短链接空值跳转前缀 Key
     * <p>
     * 这里用冒号分隔（参考实现写的是 goto_，与它自己的其它 Key 风格不一致）：
     * 本类里的 Key 全部以冒号分层，保持一致比逐字复刻参考实现更重要。
     */
    public static final String GOTO_IS_NULL_SHORT_LINK_KEY = "clearlink:is-null:goto:%s";

    /**
     * 短链接跳转锁前缀 Key
     */
    public static final String LOCK_GOTO_SHORT_LINK_KEY = "clearlink:lock:goto:%s";

    /**
     * 短链接修改分组 ID 锁前缀 Key
     */
    public static final String LOCK_GID_UPDATE_KEY = "clearlink:lock:update-gid:%s";

    /**
     * 短链接监控消息保存队列 Topic 缓存标识
     */
    public static final String SHORT_LINK_STATS_STREAM_TOPIC_KEY = "clearlink:stats-stream";

    /**
     * 短链接统计判断是否新用户缓存标识
     * <p>
     * 用 Set 保存该短链接出现过的 uv，用来判断这次访问是不是这个访客的第一次。
     * 注意：这个 Set 没有过期时间（与参考实现一致），成员会随独立访客数持续增长。
     */
    public static final String SHORT_LINK_STATS_UV_KEY = "clearlink:stats:uv:";

    /**
     * 短链接统计判断是否新 IP 缓存标识
     */
    public static final String SHORT_LINK_STATS_UIP_KEY = "clearlink:stats:uip:";
}
