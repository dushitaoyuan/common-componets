-- 操作日志表 op_log
CREATE TABLE IF NOT EXISTS op_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator     VARCHAR(64) DEFAULT NULL,
    biz_type     VARCHAR(64) NOT NULL,
    sub_biz_type VARCHAR(32) DEFAULT NULL,
    op_desc      VARCHAR(255) DEFAULT NULL,
    op_time      TIMESTAMP NOT NULL,
    op_object    VARCHAR(128) DEFAULT NULL,
    success      BOOLEAN DEFAULT NULL,
    trace_id     VARCHAR(128) DEFAULT NULL,
    tenant       VARCHAR(32) DEFAULT NULL,
    cost_time    BIGINT DEFAULT NULL,
    error_msg    CLOB DEFAULT NULL,
    op_date      VARCHAR(10) DEFAULT NULL,
    ext          CLOB DEFAULT NULL
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_op_object ON op_log (tenant, biz_type, op_object, op_time);
CREATE INDEX IF NOT EXISTS idx_op_operator ON op_log (tenant, biz_type, operator, op_time);
CREATE INDEX IF NOT EXISTS idx_op_time ON op_log (tenant, biz_type, op_time);
CREATE INDEX IF NOT EXISTS idx_trace_id ON op_log (tenant, biz_type, trace_id);
CREATE INDEX IF NOT EXISTS idx_sub_biz_type ON op_log (tenant, biz_type, sub_biz_type);
CREATE INDEX IF NOT EXISTS idx_op_date ON op_log (tenant, biz_type, op_date);

-- 操作日志详情表 op_log_detail
CREATE TABLE IF NOT EXISTS op_log_detail (
    op_log_id BIGINT PRIMARY KEY,
    op_dsl    CLOB
);