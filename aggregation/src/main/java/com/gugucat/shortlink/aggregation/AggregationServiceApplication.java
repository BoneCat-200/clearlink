package com.gugucat.shortlink.aggregation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 短链接聚合服务应用启动器
 * <p>
 * 把 admin 与 project 两个模块装进同一个 Spring 容器：
 * 对外仍然只有一套接口，内部从「HTTP 调用」变成「同一个 JVM 里的方法调用」，
 * 适合部署资源有限、又不需要独立扩缩容的场景。
 * <p>
 * 两个模块各自带有启动类，这里扫描它们的包后，启动类上的
 * {@code @EnableFeignClients}、{@code @EnableScheduling}、{@code @MapperScan}
 * 也会一并生效；这里显式补上调度与 Mapper 扫描，避免依赖扫描顺序。
 */
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.gugucat.shortlink.admin",
        "com.gugucat.shortlink.project",
        "com.gugucat.shortlink.aggregation"
})
@MapperScan(value = {
        "com.gugucat.shortlink.admin.dao.mapper",
        "com.gugucat.shortlink.project.dao.mapper"
})
public class AggregationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AggregationServiceApplication.class, args);
    }
}
