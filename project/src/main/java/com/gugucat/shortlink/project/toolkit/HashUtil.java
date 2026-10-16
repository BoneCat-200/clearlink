package com.gugucat.shortlink.project.toolkit;

import cn.hutool.core.lang.hash.MurmurHash;

/**
 * HASH 工具类
 */
public class HashUtil {

    private static final char[] CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };
    private static final int SIZE = CHARS.length;

    /**
     * 将十进制数转换成62进制数
     *
     * @param num 十进制数
     * @return 62进制字符串
     */
    private static String convertDecToBase62(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            int i = (int) (num % SIZE);
            sb.append(CHARS[i]);
            num /= SIZE;
        }
        return sb.reverse().toString();
    }

    /**
     * 将字符串转换成62进制数
     *
     * @param str 待转换字符串
     * @return 62进制字符串
     */
    public static String hashToBase62(String str) {
        int i = MurmurHash.hash32(str);
        // MurmurHash 返回值可能为负，用 Integer.MAX_VALUE - i 把它翻成正数，
        // 直接取绝对值在 i == Integer.MIN_VALUE 时会溢出
        long num = i < 0 ? Integer.MAX_VALUE - (long) i : i;
        return convertDecToBase62(num);
    }
}
