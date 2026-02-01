CREATE TABLE `op_log`
(
    `id`           bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键' ,
    `operator`     varchar(64)          DEFAULT NULL COMMENT '操作人',
    `biz_type`     varchar(64) NOT NULL COMMENT '业务类型',
    `sub_biz_type` varchar(32)          DEFAULT NULL COMMENT '操作子类型',
    `op_desc`      varchar(255)         DEFAULT NULL COMMENT '操作描述',
    `op_time`      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '操作时间',
    `op_object`    varchar(128)         DEFAULT NULL COMMENT '操作对象',
    `success`      tinyint(2)           DEFAULT NULL COMMENT '操作是否成功',
    `trace_id`     varchar(128)         DEFAULT NULL COMMENT '链路追踪id',
    `tenant`       varchar(32)          DEFAULT NULL COMMENT '租户标识',
    `cost_time`    bigint(20)           DEFAULT NULL COMMENT '耗时(毫秒)',
    `error_msg`    text                 DEFAULT NULL COMMENT '错误信息',
    `op_date`      varchar(10)          DEFAULT NULL COMMENT '操作日期',
    `ext`          text                 DEFAULT NULL COMMENT '扩展信息',
    PRIMARY KEY (`id`),
    INDEX idx_op_object (`tenant`, `biz_type`, `op_object`, `op_time`),
    INDEX idx_op_operator (`tenant`, `biz_type`, `operator`, `op_time`),
    INDEX idx_op_time (`tenant`, `biz_type`, `op_time`),
    INDEX idx_trace_id (`tenant`, `biz_type`, `trace_id`),
    INDEX idx_sub_biz_type (`tenant`, `biz_type`, `sub_biz_type`),
    INDEX idx_op_date (`tenant`, `biz_type`, `op_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin COMMENT ='操作日志表';


CREATE TABLE `op_log_detail`
(
    `op_log_id` bigint(20) NOT NULL,
    `op_dsl`    text COMMENT '操作数据描述',
    PRIMARY KEY (`op_log_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin COMMENT ='操作日志详情表';


