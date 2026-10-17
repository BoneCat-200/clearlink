package com.gugucat.shortlink.admin.common.enums;

import com.gugucat.shortlink.admin.common.convention.errorcode.IErrorCode;

public enum UserErrorCodeEnum implements IErrorCode {

    USER_NULL("B000200","用户记录不存在"),
    USER_EXIST("B000201","用户记录存在"),
    USER_NAME_EXIST("B000202","用户名已存在"),
    /**
     * 登录失败统一提示
     * <p>
     * 不区分「用户不存在」与「密码错误」：否则调用方可以逐个用户名试错，
     * 从错误信息的差异判断出哪些账号真实存在（账号枚举）。
     */
    USER_LOGIN_ERROR("B000203","用户名或密码错误");

    private final String code;

    private final String message;

    UserErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
