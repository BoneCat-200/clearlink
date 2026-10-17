package com.gugucat.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体
 * <p>
 * 只追加、不更新、不删除，因此没有 update_time / del_flag。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_operation_log")
public class OperationLogDO {

    /**
     * `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID'
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * `trace_id` varchar(64) COMMENT '链路追踪 ID'
     */
    private String traceId;

    /**
     * `username` varchar(64) COMMENT '操作人'
     */
    private String username;

    /**
     * `user_id` varchar(64) COMMENT '操作人 ID'
     */
    private String userId;

    /**
     * `module` varchar(64) COMMENT '业务模块'
     */
    private String module;

    /**
     * `action` varchar(64) COMMENT '操作动作'
     */
    private String action;

    /**
     * `java_method` varchar(128) COMMENT 'Java 方法名'
     */
    private String javaMethod;

    /**
     * `path` varchar(256) COMMENT '请求路径'
     */
    private String path;

    /**
     * `request_method` varchar(16) COMMENT 'HTTP 方法'
     */
    private String requestMethod;

    /**
     * `request_params` text COMMENT '脱敏后的请求参数'
     */
    private String requestParams;

    /**
     * `response_code` varchar(32) COMMENT '业务返回码'
     */
    private String responseCode;

    /**
     * `success` tinyint(1) COMMENT '是否成功'
     */
    private Boolean success;

    /**
     * `error_message` varchar(512) COMMENT '失败原因'
     */
    private String errorMessage;

    /**
     * `ip` varchar(64) COMMENT '客户端 IP'
     */
    private String ip;

    /**
     * `user_agent` varchar(512) COMMENT 'User-Agent'
     */
    private String userAgent;

    /**
     * `cost_ms` bigint COMMENT '耗时（毫秒）'
     */
    private Long costMs;

    /**
     * `create_time` datetime COMMENT '操作时间'
     */
    private LocalDateTime createTime;
}
