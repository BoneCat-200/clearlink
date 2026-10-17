package com.gugucat.shortlink.admin.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志响应
 */
@Data
public class OperationLogRespDTO {

    /**
     * ID
     */
    private Long id;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 操作人
     */
    private String username;

    /**
     * 业务模块
     */
    private String module;

    /**
     * 操作动作
     */
    private String action;

    /**
     * Java 方法名
     */
    private String javaMethod;

    /**
     * 请求路径
     */
    private String path;

    /**
     * HTTP 方法
     */
    private String requestMethod;

    /**
     * 脱敏后的请求参数
     */
    private String requestParams;

    /**
     * 业务返回码
     */
    private String responseCode;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 客户端 IP
     */
    private String ip;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 耗时（毫秒）
     */
    private Long costMs;

    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
