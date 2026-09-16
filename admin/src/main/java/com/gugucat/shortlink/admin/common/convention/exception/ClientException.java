package com.gugucat.shortlink.admin.common.convention.exception;


import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.gugucat.shortlink.admin.common.convention.errorcode.IErrorCode;

/**
 * 客户端异常
 */
public class ClientException extends AbstractException {

    /**
     * 构造方法：错误码枚举对象
     * @param errorCode
     */
    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    /**
     * 构造方法：错误信息
     * @param message
     */
    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    /**
     * 构造方法：错误信息，错误码对象
     * @param message
     * @param errorCode
     */
    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);

    }

    /**
     * 底层构造方法：错误信息，异常对象，错误码对象
     * @param message
     * @param throwable
     * @param errorCode
     */
    public ClientException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);   // 将原始异常连带错误信息向上传递
    }

    @Override
    public String toString() {
        return "ClientException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}

