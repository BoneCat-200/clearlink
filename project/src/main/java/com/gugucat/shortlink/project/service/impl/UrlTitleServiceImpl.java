package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.util.StrUtil;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.service.UrlTitleService;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * URL 标题接口实现层
 */
@Service
public class UrlTitleServiceImpl implements UrlTitleService {

    /**
     * 抓取目标网站的超时时间，单位毫秒
     * <p>
     * 目标站点是外部服务，一定会出现超时、断连、返回畸形数据。不设上限的话，
     * 一个不响应的站点会把调用线程一直挂住。
     */
    private static final int FETCH_TIMEOUT_MS = 5000;

    @Override
    public String getTitleByUrl(String url) {
        if (StrUtil.isBlank(url)) {
            throw new ClientException("目标网站地址不能为空");
        }
        try {
            // 只发一次请求：jsoup 遇到非 2xx 会抛 HttpStatusException，
            // 不需要先另发一个请求去探响应码（参考实现那样会把目标站点的请求量翻倍，
            // 而且它打开的 HttpURLConnection 没有关闭）
            Document document = Jsoup.connect(url).timeout(FETCH_TIMEOUT_MS).get();
            return document.title();
        } catch (HttpStatusException ex) {
            // 目标站点响应了，但状态码不是 2xx。它继承自 IOException，必须排在 IOException 之前
            throw new ClientException(String.format("目标网站返回状态码 %s，无法获取标题", ex.getStatusCode()));
        } catch (IllegalArgumentException ex) {
            // jsoup 对格式非法的地址（比如用户手输的 "abc"）直接抛 IllegalArgumentException，
            // 不接住的话会一路冒到全局异常处理器、变成一句「系统执行出错」
            throw new ClientException("目标网站地址格式不正确");
        } catch (IOException ex) {
            // 域名解析不了、连接被拒、超时都会走到这里
            throw new ClientException("获取目标网站标题失败：" + ex.getMessage());
        }
    }
}
