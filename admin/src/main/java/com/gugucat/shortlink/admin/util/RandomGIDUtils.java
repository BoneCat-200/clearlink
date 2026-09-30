package com.gugucat.shortlink.admin.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 短链接gid随机生成工具类
 */
public final class RandomGIDUtils {

    /** 数字 + 大小写字母 62 个字符 */
    private static final char[] CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /** 生成的随机码长度 */
    private static final int CODE_LENGTH = 6;

    /**
     * 生成 6 位随机数（数字 + 英文字母）
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS[ThreadLocalRandom.current().nextInt(CHARS.length)]);
        }
        return sb.toString();
    }
}
