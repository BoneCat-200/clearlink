package com.gugucat.shortlink.project.toolkit;

import cn.hutool.core.util.StrUtil;

import java.net.URI;
import java.util.Date;
import java.util.Optional;

import static com.gugucat.shortlink.project.common.constant.ShortLinkConstant.DEFAULT_CACHE_VALID_TIME;

/**
 * 短链接工具类
 */
public class LinkUtil {

    /**
     * 获取短链接缓存有效期时间
     * <p>
     * 返回的是「距离失效还有多久」：永久有效返回默认值，<b>已过期返回非正数</b>，
     * 调用方必须先判断再决定是否写缓存 —— 给 Redis 传一个非正的过期时间会直接报错。
     * <p>
     * 这里不能用 hutool 的 {@code DateUtil.between}：它返回的是绝对差值，
     * 一个已经过期的短链接会被算成「从过期那天到现在有多久」，也就是一个以年计的正数，
     * 缓存因此被长期保留，跳转的缓存命中分支又不校验有效期，结果就是过期链接一直能跳。
     *
     * @param validDate 有效期时间，null 表示永久有效
     * @return 缓存存活毫秒数；小于等于 0 表示不应缓存
     */
    public static long getLinkCacheValidTime(Date validDate) {
        return Optional.ofNullable(validDate)
                .map(each -> each.getTime() - System.currentTimeMillis())
                .orElse(DEFAULT_CACHE_VALID_TIME);
    }

    /**
     * 判断短链接是否已过有效期
     *
     * @param validDate 有效期时间，null 表示永久有效
     * @return 是否已过期
     */
    public static boolean isExpired(Date validDate) {
        return validDate != null && validDate.before(new Date());
    }

    /**
     * 获取原始链接中的域名
     * 如果原始链接包含 www 开头的话需要去掉
     *
     * @param url 创建或者修改短链接的原始链接
     * @return 原始链接中的域名，解析失败返回 null
     */
    public static String extractDomain(String url) {
        String domain = null;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (StrUtil.isNotBlank(host)) {
                domain = host;
                if (domain.startsWith("www.")) {
                    domain = host.substring(4);
                }
            }
        } catch (Exception ignored) {
        }
        return domain;
    }
}
