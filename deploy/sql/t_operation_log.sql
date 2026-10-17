USE link;

-- 操作审计日志（admin 入口的统一留痕）
--
-- 说明：
--   1. 单表，不加入 ShardingSphere 分片规则，未配置的表会走默认数据源 ds_0；
--   2. 审计日志只追加，不更新、不删除，所以没有 update_time / del_flag；
--   3. request_params 存脱敏后的参数 JSON，长度按 2000 字符截断。

CREATE TABLE IF NOT EXISTS t_operation_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    trace_id        VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪 ID',
    username        VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    user_id         VARCHAR(64)  DEFAULT NULL COMMENT '操作人 ID',
    module          VARCHAR(64)  NOT NULL COMMENT '业务模块',
    action          VARCHAR(64)  NOT NULL COMMENT '操作动作',
    java_method     VARCHAR(128) DEFAULT NULL COMMENT 'Java 方法名',
    path            VARCHAR(256) DEFAULT NULL COMMENT '请求路径',
    request_method  VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP 方法',
    request_params  TEXT         DEFAULT NULL COMMENT '脱敏后的请求参数',
    response_code   VARCHAR(32)  DEFAULT NULL COMMENT '业务返回码',
    success         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功',
    error_message   VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    ip              VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    user_agent      VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
    cost_ms         BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_username_time (username, create_time),
    KEY idx_trace_id (trace_id),
    KEY idx_module_time (module, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';
