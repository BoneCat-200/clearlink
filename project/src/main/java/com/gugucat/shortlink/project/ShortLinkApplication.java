package com.gugucat.shortlink.project;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 短链接中台应用启动类
 */
@SpringBootApplication
@EnableScheduling   // 统计消息的待确认消息回收任务靠它驱动
@MapperScan("com.gugucat.shortlink.project.dao.mapper")  // 注册Mapper扫描路径，将mapper接口注册到Spring容器中
public class ShortLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkApplication.class, args);
    }
}
