package com.gugucat.shortlink.admin.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis布隆过滤器配置类
 */
@Configuration  // 标记配置类
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
