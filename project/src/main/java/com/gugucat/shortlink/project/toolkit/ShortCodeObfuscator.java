package com.gugucat.shortlink.project.toolkit;

import java.math.BigInteger;

/**
 * 短码可逆混淆器
 * <p>
 * 发号器发的是连续递增的 ID，直接 Base62 会得到可预测、可枚举的短码
 * （{@code ...A9Z、Aa0、Aa1...}，攻击者 +1 就能遍历全站）。
 * 这里用仿射变换 {@code f(x) = (A * x + B) mod M} 把顺序 ID 打散：
 * <ul>
 *   <li>{@code M = 62^6}，对应 6 位短码空间；</li>
 *   <li>{@code A} 与 {@code M} 互质时，f 是 [0, M) 上的双射，<b>短码不会重复</b>；</li>
 *   <li>解码只需模逆元 {@code A⁻¹}，用于测试与排查。</li>
 * </ul>
 * <p>
 * 两个硬约束：
 * <ol>
 *   <li>A、B 一旦上线不能改。改了之后老短码解码会指向错误的 ID；
 *       要换参数必须引入新的编码版本前缀。</li>
 *   <li>这里的混淆目标是「提高顺手 +1 枚举的成本」，不是密码学强度。
 *       拿着多个已知短码做定向分析仍可能反解参数，这是可接受的取舍。</li>
 * </ol>
 * <p>
 * 实现参考了短链接系统常见的「发号器 + 可逆混淆」方案；仿射变换的数学性质
 * 保证唯一性不依赖任何中间件状态。
 */
public final class ShortCodeObfuscator {

    /**
     * 短码长度
     */
    public static final int CODE_LENGTH = 6;

    /**
     * 短码空间大小：62^6 = 56,800,235,584
     */
    public static final long MODULUS = 56_800_235_584L;

    /**
     * A 的上限：保证 {@code id * A} 不会溢出 long（id < 62^6）
     */
    private static final long MAX_A = 100_000_000L;

    /**
     * 混淆参数 A
     */
    private final long a;

    /**
     * 混淆参数 B（已归一到 [0, M)）
     */
    private final long b;

    /**
     * A 在模 M 下的逆元
     */
    private final long aInverse;

    /**
     * 构造混淆器
     *
     * @param a 乘数，必须与 62 互质（奇数且不是 31 的倍数）
     * @param b 加数，非负
     */
    public ShortCodeObfuscator(long a, long b) {
        if (a <= 0 || a >= MAX_A || a % 2 == 0 || a % 31 == 0) {
            throw new IllegalArgumentException(
                    "A 必须是 (0, " + MAX_A + ") 内与 62 互质的整数（奇数且不能是 31 的倍数）：" + a);
        }
        if (b < 0) {
            throw new IllegalArgumentException("B 不能为负数：" + b);
        }
        this.a = a;
        this.b = Math.floorMod(b, MODULUS);
        // A 与 M 互质时模逆元一定存在；求不出来说明参数非法
        this.aInverse = BigInteger.valueOf(a)
                .modInverse(BigInteger.valueOf(MODULUS))
                .longValue();
    }

    /**
     * ID 编码为 6 位短码
     *
     * @param id 发号器给出的 ID
     * @return 定长 6 位的 Base62 短码
     */
    public String encode(long id) {
        if (id < 0 || id >= MODULUS) {
            // 超过 6 位空间后仿射变换会回绕，继续发号就会产生重复短码，
            // 必须在入口处拒绝，而不是悄悄发出错误的值
            throw new IllegalStateException("短码空间已耗尽，id：" + id);
        }
        long x = (id * a + b) % MODULUS;
        String base62 = Base62Util.encode(x);
        if (base62.length() >= CODE_LENGTH) {
            return base62;
        }
        return "0".repeat(CODE_LENGTH - base62.length()) + base62;
    }

    /**
     * 短码解码回 ID（用于测试与问题排查）
     *
     * @param code 6 位短码
     * @return 原始 ID
     */
    public long decode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("短码长度必须为 " + CODE_LENGTH + "：" + code);
        }
        long x = Base62Util.decode(code);
        // (x - B) * A⁻¹ 的中间值可能超过 long，必须用 BigInteger
        BigInteger id = BigInteger.valueOf(x)
                .subtract(BigInteger.valueOf(b))
                .multiply(BigInteger.valueOf(aInverse))
                .mod(BigInteger.valueOf(MODULUS));
        return id.longValue();
    }
}
