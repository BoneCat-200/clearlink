USE link;

-- 统计对账报告：每次对账生成一行，记录参与数、一致数、不一致数与失败原因。
-- 明细只保存前 N 条（由 short-link.reconcile.max-detail-size 控制），计数是全量值。
CREATE TABLE IF NOT EXISTS `t_reconcile_report` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `biz_date`        date         NOT NULL COMMENT '对账业务日期',
    `trigger_type`    varchar(16)  NOT NULL COMMENT '触发方式：schedule/manual',
    `operator`        varchar(64)  DEFAULT NULL COMMENT '手动触发的操作人',
    `total_links`     bigint       NOT NULL DEFAULT '0' COMMENT '参与对账的短链接数',
    `matched_links`   bigint       NOT NULL DEFAULT '0' COMMENT 'PV 一致的短链接数',
    `mismatch_links`  bigint       NOT NULL DEFAULT '0' COMMENT 'PV 不一致的短链接数',
    `status`          tinyint      NOT NULL DEFAULT '0' COMMENT '状态：0 执行中，1 成功，2 失败',
    `mismatch_detail` text COMMENT '不一致明细 JSON，最多保存 max-detail-size 条',
    `error_message`   varchar(512) DEFAULT NULL COMMENT '失败原因',
    `cost_ms`         bigint       DEFAULT NULL COMMENT '耗时（毫秒）',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_biz_date` (`biz_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='统计对账报告';
