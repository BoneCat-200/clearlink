package com.gugucat.shortlink.project.toolkit;

/**
 * Base62 编解码工具
 * <p>
 * 字符集为 {@code 0-9A-Za-z}，共 62 个，全部是 URL 安全字符。
 * 与 {@link HashUtil} 里的转换相比，这里额外支持 0 与反向解码：
 * 发号器的 ID 经过仿射变换后可能落在 0 上，且测试需要验证编码可逆。
 */
public final class Base62Util {

    /**
     * Base62 字符表，下标即数值
     */
    private static final char[] CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    /**
     * 进制
     */
    public static final int RADIX = CHARS.length;

    private Base62Util() {
    }

    /**
     * 十进制转 Base62
     *
     * @param num 非负整数
     * @return Base62 字符串；0 返回 "0"
     */
    public static String encode(long num) {
        if (num < 0) {
            throw new IllegalArgumentException("Base62 只支持非负数：" + num);
        }
        if (num == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(CHARS[(int) (num % RADIX)]);
            num /= RADIX;
        }
        return sb.reverse().toString();
    }

    /**
     * Base62 转十进制
     *
     * @param str Base62 字符串
     * @return 十进制数值
     */
    public static long decode(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Base62 字符串不能为空");
        }
        long num = 0;
        for (char c : str.toCharArray()) {
            int digit = digitOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("非法 Base62 字符：" + c);
            }
            // 用精确运算把溢出变成异常，而不是悄悄得到一个错误的 ID
            num = Math.addExact(Math.multiplyExact(num, RADIX), digit);
        }
        return num;
    }

    /**
     * 取字符对应的数值
     *
     * @param c 字符
     * @return 0~61；非法字符返回 -1
     */
    private static int digitOf(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 36;
        }
        return -1;
    }
}
