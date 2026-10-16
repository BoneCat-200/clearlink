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

    /**
     * 高德获取地区接口地址
     * <p>
     * 只有 IP 一个入参，返回省/市与行政区划编码。用它做逆地理是因为它不需要经纬度，
     * 而跳转链路里能拿到的只有客户端 IP。
     */
    public static final String AMAP_REMOTE_URL = "https://restapi.amap.com/v3/ip";

    /**
     * 高德接口超时时间，单位毫秒
     * <p>
     * 这个调用发生在消息消费里，是同步的。不给超时的话，高德那边一旦挂住，
     * 单线程的消费者就会一直卡在这一条消息上，后面所有统计都跟着停
     * —— 统计是旁路，绝不能反过来拖垮主链路。
     */
    public static final int AMAP_TIME_OUT = 3000;
}
