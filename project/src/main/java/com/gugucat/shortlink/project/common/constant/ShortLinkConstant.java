package com.gugucat.shortlink.project.common.constant;

/**
 * 短链接常量类
 */
public class ShortLinkConstant {

    /**
     * 永久短链接默认缓存有效时间，2626560000 毫秒 ≈ 30.4 天
     */
    public static final long DEFAULT_CACHE_VALID_TIME = 2626560000L;

    /**
     * 短链接不存在时的空值缓存有效时间，单位分钟
     * <p>
     * 取 30 分钟是权衡：太短挡不住穿透，太长会让「刚被改成有效」的短链接在这个窗口内继续查不到，
     * 所以修改短链接那条链路必须在续期成功时主动删掉这个缓存。
     */
    public static final long NULL_CACHE_VALID_TIME = 30;
}
