-- 操作日志表 op_log
CREATE TABLE "op_log"
(
    "id"           INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    "operator"     TEXT               DEFAULT NULL,
    "biz_type"     TEXT      NOT NULL,
    "op_desc"      TEXT               DEFAULT NULL,
    "sub_biz_type" TEXT               DEFAULT NULL,
    "op_time"      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "op_object"    TEXT               DEFAULT NULL,
    "success"      INTEGER            DEFAULT NULL,
    "trace_id"     TEXT               DEFAULT NULL,
    "tenant"       TEXT               DEFAULT NULL,
    "cost_time"    INTEGER            DEFAULT NULL,
    "error_msg"    TEXT               DEFAULT NULL,
    "op_date"      TEXT               DEFAULT NULL,
    "ext"          TEXT               DEFAULT NULL
);

-- 索引
CREATE INDEX "idx_op_object" ON "op_log" ("tenant", "biz_type",  "op_object","op_time");
CREATE INDEX "idx_op_operator" ON "op_log" ("tenant", "biz_type", "operator","op_time");
CREATE INDEX "idx_op_time" ON "op_log" ("tenant", "biz_type", "op_time");
CREATE INDEX "idx_trace_id" ON "op_log" ("tenant", "biz_type", "trace_id");
CREATE INDEX "idx_trace_biz_type" ON "op_log" ("tenant", "biz_type", "sub_biz_type");
CREATE INDEX "idx_op_date" ON "op_log" ("tenant", "biz_type", "op_date");


-- 操作日志详情表 op_log_detail
CREATE TABLE "op_log_detail"
(
    "op_log_id" INTEGER NOT NULL PRIMARY KEY,
    "op_dsl"    TEXT
);
