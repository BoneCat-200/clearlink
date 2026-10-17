package com.gugucat.shortlink.project.toolkit;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

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
     * 获取用户真实IP
     * <p>
     * 优先取各种代理头：经过 Nginx / 网关之后 request.getRemoteAddr() 拿到的是代理的地址，
     * 只有 X-Forwarded-For 这类头里才有真实客户端 IP。
     *
     * @param request 请求
     * @return 用户真实IP
     */
    public static String getActualIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    /**
     * 获取用户访问操作系统
     * <p>
     * <b>判断顺序不能随意调换</b>：移动端的 UA 里同时含有桌面系统的关键字，
     * 必须让更具体的判断排在前面，否则会被错误归类。
     * <ul>
     *   <li>{@code android} 必须排在 {@code linux} 之前 ——
     *       Android 的 UA 形如 {@code (Linux; Android 13; ...)}，两者都命中</li>
     *   <li>{@code iphone}/{@code ipad} 必须排在 {@code mac} 之前 ——
     *       iOS 的 UA 形如 {@code (iPhone; CPU iPhone OS 17_0 like Mac OS X)}，两者都命中</li>
     * </ul>
     * 参考实现把 {@code mac} 和 {@code linux} 放在前面，导致 iOS 被记成 Mac OS、
     * Android 被记成 Linux，这两个维度实际上收不到移动端的数据。
     *
     * @param request 请求
     * @return 访问操作系统
     */
    public static String getOs(HttpServletRequest request) {
        // UA 是客户端自愿携带的请求头，缺省时 getHeader 返回 null。
        // 直接 .toLowerCase() 会空指针，而调用它的跳转链路在 sendRedirect 之前 ——
        // 结果是「少一个请求头」就让短链接完全跳不动（实测：不带 UA 时返回 200 而不是 302）
        String userAgent = StrUtil.blankToDefault(request.getHeader("User-Agent"), "").toLowerCase();
        if (userAgent.contains("windows")) {
            return "Windows";
        } else if (userAgent.contains("android")) {
            return "Android";
        } else if (userAgent.contains("iphone") || userAgent.contains("ipad")) {
            return "iOS";
        } else if (userAgent.contains("mac")) {
            return "Mac OS";
        } else if (userAgent.contains("linux")) {
            return "Linux";
        } else {
            return "Unknown";
        }
    }

    /**
     * 获取用户访问浏览器
     *
     * @param request 请求
     * @return 访问浏览器
     */
    public static String getBrowser(HttpServletRequest request) {
        // 同 getOs：UA 缺省时不能空指针
        String userAgent = StrUtil.blankToDefault(request.getHeader("User-Agent"), "").toLowerCase();
        if (userAgent.contains("edg")) {
            return "Microsoft Edge";
        } else if (userAgent.contains("chrome")) {
            return "Google Chrome";
        } else if (userAgent.contains("firefox")) {
            return "Mozilla Firefox";
        } else if (userAgent.contains("safari")) {
            return "Apple Safari";
        } else if (userAgent.contains("opera")) {
            return "Opera";
        } else if (userAgent.contains("msie") || userAgent.contains("trident")) {
            return "Internet Explorer";
        } else {
            return "Unknown";
        }
    }

    /**
     * 获取用户访问设备
     *
     * @param request 请求
     * @return 访问设备
     */
    public static String getDevice(HttpServletRequest request) {
        String userAgent = StrUtil.blankToDefault(request.getHeader("User-Agent"), "").toLowerCase();
        if (userAgent.isEmpty()) {
            // 没有 UA 就判断不出设备。这里不能沿用「不含 mobile 就算 PC」的兜底 ——
            // 那会把所有爬虫、脚本、健康检查都算成 PC，凭空抬高 PC 的占比
            return "Unknown";
        }
        if (userAgent.contains("mobile")) {
            return "Mobile";
        }
        return "PC";
    }

    /**
     * 获取用户访问网络
     * <p>
     * 这里用的是「IP 段猜测」这种粗糙办法，真实的网络类型需要查 IP 库或三方服务。
     *
     * @param request 请求
     * @return 访问网络
     */
    public static String getNetwork(HttpServletRequest request) {
        String actualIp = getActualIp(request);
        return actualIp.startsWith("192.168.") || actualIp.startsWith("10.") ? "WIFI" : "Mobile";
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
