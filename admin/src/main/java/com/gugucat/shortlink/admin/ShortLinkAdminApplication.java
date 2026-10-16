package com.gugucat.shortlink.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.gugucat.shortlink.admin.dao.mapper") // 注册Mapper扫描路径，将mapper接口注册到Spring容器中
@EnableFeignClients("com.gugucat.shortlink.admin.remote") // 扫描短链接中台远程调用接口
public class ShortLinkAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShortLinkAdminApplication.class, args);
    }
}
