package com.gugucat.shortlink.project.generator;

import com.gugucat.shortlink.project.toolkit.ShortCodeObfuscator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于号段发号器的短码生成器
 * <p>
 * 流程：数据库领号段 → 本地取 ID → 仿射混淆 → Base62 定长编码。
 * 唯一性由数据库原子分配号段保证，不依赖布隆过滤器或全局锁。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.code.generator", havingValue = "segment", matchIfMissing = true)
public class SegmentShortCodeGenerator implements ShortCodeGenerator {

    private final SegmentIdGenerator segmentIdGenerator;

    private final ShortCodeObfuscator shortCodeObfuscator;

    /**
     * 生成短码
     * <p>
     * domain 与 originUrl 在这个实现里不参与计算：号段本身全局唯一，
     * 也不需要按域名判重。保留参数是为了与哈希实现共用同一个接口。
     *
     * @param domain    建链域名（未使用）
     * @param originUrl 原始链接（未使用）
     * @return 6 位短码
     */
    @Override
    public String nextCode(String domain, String originUrl) {
        return shortCodeObfuscator.encode(segmentIdGenerator.nextId());
    }
}
