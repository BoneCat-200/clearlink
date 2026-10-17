package com.gugucat.shortlink.gateway.config;

import lombok.Data;

import java.util.List;

/**
 * Token 校验过滤器的参数
 * <p>
 * 对应路由配置里 {@code filters[].args} 的内容，由配置绑定而来。
 */
@Data
public class TokenValidateFilterConfig {

    /**
     * 无需登录即可访问的路径（精确匹配）
     */
    private List<String> whitePathList;
}
