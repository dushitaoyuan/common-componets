package com.taoyuanx.common.audit.log.runtime.sql;

/**
 * SQL 模板管理类
 * <p>
 * 统一管理所有 SQL 模板，支持动态替换表名
 * </p>
 *
 * @author taoyuan
 * @date 2025/7/29
 */
public class SqlTemplateManager {

    /**
     * 插入日志主表的 SQL 模板（不包含 ID，使用数据库自增）
     */
    public static final String INSERT_SQL_TEMPLATE =
        "INSERT INTO %s (operator, biz_type, sub_biz_type, op_desc, op_time, op_object, success, trace_id, tenant, cost_time, error_msg, op_date, ext) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * 插入日志主表的 SQL 模板（包含 ID，使用雪花算法生成）
     */
    public static final String INSERT_SQL_WITH_ID_TEMPLATE =
        "INSERT INTO %s (id, operator, biz_type, sub_biz_type, op_desc, op_time, op_object, success, trace_id, tenant, cost_time, error_msg, op_date, ext) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * 插入日志单表的 SQL 模板（不包含 ID，使用数据库自增）
     */
    public static final String INSERT_SQL_WITH_DSL_TEMPLATE =
            "INSERT INTO %s (operator, biz_type, sub_biz_type, op_desc, op_time, op_object, success, trace_id, tenant, cost_time, error_msg, op_date, ext,op_dsl) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)";

    /**
     * 插入日志单表的 SQL 模板（包含 ID，使用雪花算法生成）
     */
    public static final String INSERT_SQL_WITH_DSL_WITH_ID_TEMPLATE =
            "INSERT INTO %s (id, operator, biz_type, sub_biz_type, op_desc, op_time, op_object, success, trace_id, tenant, cost_time, error_msg, op_date, ext,op_dsl) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)";


    /**
     * 插入日志详情表的 SQL 模板
     */
    public static final String INSERT_DETAIL_SQL_TEMPLATE =
        "INSERT INTO %s (op_log_id, op_dsl) VALUES (?, ?)";

    /**
     * 查询日志主表的 SQL 模板
     */
    public static final String QUERY_SQL_BASE_TEMPLATE =
        "SELECT id, operator, biz_type, sub_biz_type, op_desc, op_time, op_object, success, trace_id, tenant, cost_time, op_date FROM %s WHERE 1=1";

    /**
     * 查询日志总数的 SQL 模板
     */
    public static final String QUERY_COUNT_SQL_BASE_TEMPLATE =
        "SELECT count(*) FROM %s WHERE 1=1";

    /**
     * 查询日志详情的 SQL 模板
     */
    public static final String QUERY_DETAIL_SQL_TEMPLATE =
        "SELECT op_dsl FROM %s WHERE op_log_id = ?";

    /**
     * 获取插入 SQL（替换表名，不包含 ID）
     */
    public static String getInsertSql(String tableName,boolean enableLogDetailTable) {
        if(enableLogDetailTable){
            return String.format(INSERT_SQL_TEMPLATE, tableName);
        }
        return String.format(INSERT_SQL_WITH_DSL_TEMPLATE, tableName);
    }

    /**
     * 获取插入 SQL（替换表名，包含 ID）
     */
    public static String getInsertSqlWithId(String tableName,boolean enableLogDetailTable) {
        if(enableLogDetailTable){
            return String.format(INSERT_SQL_WITH_ID_TEMPLATE, tableName);
        }
        return String.format(INSERT_SQL_WITH_DSL_WITH_ID_TEMPLATE, tableName);
    }

    /**
     * 获取插入详情 SQL（替换表名）
     */
    public static String getInsertDetailSql(String detailTableName) {
        return String.format(INSERT_DETAIL_SQL_TEMPLATE, detailTableName);
    }

    /**
     * 获取查询 SQL（替换表名）
     */
    public static String getQuerySql(String tableName) {
        return String.format(QUERY_SQL_BASE_TEMPLATE, tableName);
    }

    /**
     * 获取计数 SQL（替换表名）
     */
    public static String getCountSql(String tableName) {
        return String.format(QUERY_COUNT_SQL_BASE_TEMPLATE, tableName);
    }

    /**
     * 获取查询详情 SQL（替换表名）
     */
    public static String getQueryDetailSql(String detailTableName) {
        return String.format(QUERY_DETAIL_SQL_TEMPLATE, detailTableName);
    }
}