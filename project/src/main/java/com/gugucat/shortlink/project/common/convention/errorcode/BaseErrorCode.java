package com.gugucat.shortlink.project.common.convention.errorcode;

/**
 * 基础错误码规约
 * 参考：阿里开发规范-泰山版
 * <p>
 * 只登记当前模块真正会用到的错误码；后续功能用到新的码再往下追加，
 * 避免留一堆没有任何抛出点的枚举值。
 */
public enum BaseErrorCode implements IErrorCode {

    // ========== 一级宏观错误码 客户端错误 ==========
    CLIENT_ERROR("A000001", "用户端错误"),

    // ========== 一级宏观错误码 系统执行出错 ==========
    SERVICE_ERROR("B000001", "系统执行出错");

    private final String code;

    private final String message;

    BaseErrorCode(String code, String message) {
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
