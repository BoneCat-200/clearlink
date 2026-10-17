package com.gugucat.shortlink.project.generator;

import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.toolkit.HashUtil;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 基于随机哈希的短码生成器（旧实现）
 * <p>
 * 仅在 `short-link.code.generator=hash` 时启用，作用是给号段方案留一个回滚开关。
 * 逻辑与原 {@code ShortLinkServiceImpl#generateSuffix} 完全一致：
 * 原始链接拼随机 UUID → MurmurHash → Base62 → 布隆过滤器判重，最多重掷 10 次。
 * <p>
 * 不要把它当成默认实现：哈希空间只有 2^32，链接量上来后碰撞概率会显著上升，
 * 且在线上无法避免地依赖 Redis 中的布隆过滤器状态。
 */
@Component
@ConditionalOnProperty(name = "short-link.code.generator", havingValue = "hash")
public class HashShortCodeGenerator implements ShortCodeGenerator {

    /**
     * 最多重掷次数
     */
    private static final int MAX_GENERATE_COUNT = 10;

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;

    public HashShortCodeGenerator(
            @Qualifier("shortUriCreateCachePenetrationBloomFilter")
            RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter) {
        this.shortUriCreateCachePenetrationBloomFilter = shortUriCreateCachePenetrationBloomFilter;
    }

    /**
     * 生成短码
     *
     * @param domain    建链域名，用于拼布隆判重键
     * @param originUrl 原始链接
     * @return 短码
     */
    @Override
    public String nextCode(String domain, String originUrl) {
        int generateCount = 0;
        for (; ; ) {
            if (generateCount > MAX_GENERATE_COUNT) {
                throw new ServiceException("短链接频繁生成，请稍后再试");
            }
            String shortUri = HashUtil.hashToBase62(originUrl + UUID.randomUUID());
            // 判重键必须与数据库唯一索引、跳转缓存 key 一致：都用实际域名
            if (!shortUriCreateCachePenetrationBloomFilter.contains(domain + "/" + shortUri)) {
                return shortUri;
            }
            generateCount++;
        }
    }
}
