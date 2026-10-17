package com.gugucat.shortlink.project.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 建链域名配置
 * <p>
 * 只绑定 `short-link.domain.white-list`；同一前缀下的 `short-link.domain.default`
 * 由 {@code ShortLinkServiceImpl} 用 `@Value` 读取，不属于这里（Spring 会忽略
 * 没有对应字段的配置项，不会报错）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.domain")
public class CreateDomainConfiguration {

    /**
     * 允许用来建链的域名白名单
     * <p>
     * 建链时请求里可以指定域名，但**不能是任意域名**：短链接最终要能解析到本服务，
     * 允许任意域名等于让调用方造出一批指向别人域名的坏链接，还可能被拿去做跳板。
     * 留空表示只允许默认域名。
     */
    private List<String> whiteList = new ArrayList<>();
}
