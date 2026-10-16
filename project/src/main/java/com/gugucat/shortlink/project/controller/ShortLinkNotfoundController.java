package com.gugucat.shortlink.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 短链接不存在跳转控制器
 */
@Controller
public class ShortLinkNotfoundController {

    /**
     * 短链接不存在跳转页面
     * <p>
     * 返回视图名而不是 JSON：这里要的是一张给最终用户看的页面。
     * 视图名由 Thymeleaf 按默认前缀 classpath:/templates/ 解析到 templates/notfound.html，
     * 不需要额外配置 spring.mvc.view.*。
     */
    @GetMapping("/page/notfound")
    public String notfound() {
        return "notfound";
    }
}
