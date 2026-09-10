package com.gugucat.shortlink.admin.common.convention.errorcode;

/**
 * 平台错误码接口
 */
public interface IErrorCode {

    /**
     * 获取错误码
     */
    String code();

    /**
     * 获取错误信息
     */
    String message();
}
