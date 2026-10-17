package com.gugucat.shortlink.admin.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis布隆过滤器配置类
 */
@Configuration("rBloomFilterConfigurationByAdmin")  // 显式命名，避免聚合部署时与 project 模块的同名配置冲突
public class RBloomFilterConfiguration {

    /**
     * 用户注册缓存穿透布隆过滤器
     * @param redissonClient
     * @return
     */
    @Bean   // 标记为Bean
    public RBloomFilter<String> userRegisterCachePenetrationBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> userRegisterCachePenetrationBloomFilter = redissonClient.getBloomFilter("userRegisterCachePenetrationBloomFilter");
        userRegisterCachePenetrationBloomFilter.tryInit(100000000, 0.001);
        return userRegisterCachePenetrationBloomFilter;
    }


}
