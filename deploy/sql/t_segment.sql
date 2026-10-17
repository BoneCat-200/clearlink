USE link;

-- 号段发号器表
--
-- 这是发号器的元数据，不是业务数据：
--   1. 单表，不要加入 ShardingSphere 的分片规则（未配置的表会走默认数据源 ds_0）；
--   2. 所有实例必须读写同一行，才能通过 UPDATE 的行锁保证号段不重叠。
--
-- 初始 max_id = 62^5 = 916132832，保证第一个号段（+step 之后）落在 6 位空间。

CREATE TABLE IF NOT EXISTS t_segment (
    biz_tag     VARCHAR(64) NOT NULL COMMENT '业务标识，如 short-link:uri',
    max_id      BIGINT      NOT NULL COMMENT '已分配到的最大 ID',
    step        INT         NOT NULL DEFAULT 1000 COMMENT '号段长度',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='号段发号器';

-- 重复执行不会覆盖已有进度
INSERT INTO t_segment (biz_tag, max_id, step)
VALUES ('short-link:uri', 916132832, 1000)
ON DUPLICATE KEY UPDATE biz_tag = biz_tag;
