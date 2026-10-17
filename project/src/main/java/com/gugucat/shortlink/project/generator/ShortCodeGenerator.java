package com.gugucat.shortlink.project.generator;

/**
 * 短码生成器
 * <p>
 * 建链逻辑只依赖这个接口，具体用哪种算法由 `short-link.code.generator` 决定：
 * <ul>
 *   <li>{@code segment}（默认）：号段发号器 + Base62 + 仿射混淆；</li>
 *   <li>{@code hash}：旧的 MurmurHash + 布隆判重，作为回滚开关保留。</li>
 * </ul>
 * 新增算法只需要实现本接口并加一个条件装配，不需要改建链主流程。
 */
public interface ShortCodeGenerator {

    /**
     * 生成一个短码
     *
     * @param domain    本次建链使用的域名，哈希实现用它拼布隆判重键
     * @param originUrl 原始链接，哈希实现用它作为哈希输入
     * @return 短链接后缀
     */
    String nextCode(String domain, String originUrl);
}
