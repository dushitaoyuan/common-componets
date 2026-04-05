package com.taoyuanx.common.audit.log.runtime.impl;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.common.LogIdGenerator;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.ext.AuditLogQueryService;
import com.taoyuanx.common.audit.log.runtime.model.*;
import com.taoyuanx.common.audit.log.runtime.autoconfigure.AuditLogProperties;
import com.taoyuanx.common.audit.log.runtime.sql.SqlTemplateManager;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 JdbcTemplate 的审计日志服务抽象基类
 * <p>
 * 提供通用的 JDBC 操作和 SQL 模板管理
 * 子类需要实现表名计算逻辑
 * </p>
 *
 * @author taoyuan
 * @date 2025/7/29
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractJdbcTemplateAuditLogService implements AuditLogStoreService, AuditLogQueryService {

    protected final JdbcTemplate jdbcTemplate;
    protected String logTableName;
    protected String logDetailTableName;
    protected AuditLogProperties auditLogProperties;
    protected LogIdGenerator logIdGenerator;
    protected boolean enableLogDetailTable;


    public AbstractJdbcTemplateAuditLogService(JdbcTemplate jdbcTemplate, AuditLogProperties auditLogProperties, LogIdGenerator logIdGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.logTableName = auditLogProperties.getLogTableName();
        this.logDetailTableName = auditLogProperties.getLogDetailTableName();
        this.auditLogProperties = auditLogProperties;
        this.enableLogDetailTable = auditLogProperties.getEnableLogDetailTable();
        this.logIdGenerator = logIdGenerator;
    }


    @Override
    public PageModel<AuditLogViewModel> page(PageQueryModel<AuditLogQueryModel> pageQuery) {
        AuditLogQueryModel queryModel = pageQuery.getQuery();
        // 计算表名
        String tableName = calcTableName(queryModel.getTenant(), logTableName);

        StringBuilder sql = new StringBuilder(SqlTemplateManager.getQuerySql(tableName));
        Pair<String, List<Object>> conditionPair = buildQuerySql(queryModel);
        sql.append(conditionPair.getLeft());
        // 分页处理（SQLite 使用 LIMIT 和 OFFSET）
        sql.append(" ORDER BY op_time DESC LIMIT ? OFFSET ?");
        List<Object> params = conditionPair.getRight();
        params.add(pageQuery.getPageSize());
        params.add((pageQuery.getPageNum() - 1) * pageQuery.getPageSize());
        PageModel<AuditLogViewModel> pageModel = new PageModel<>();
        pageModel.setTotal(0L);
        pageModel.setList(Collections.emptyList());
        try {
            Long count = count(queryModel, tableName);
            if (count <= 0) {
                return pageModel;
            }
            pageModel.setTotal(count);
            pageModel.setList(jdbcTemplate.query(sql.toString(), logRowMapper, params.toArray()));
            return pageModel;
        } catch (EmptyResultDataAccessException e) {
            return pageModel;
        } catch (Exception e) {
            log.error("page error,query:{}", JSON.toJSONString(queryModel), e);
            throw e;
        }
    }

    /**
     * 统计数量
     */
    private Long count(AuditLogQueryModel queryModel, String tableName) {
        StringBuilder sql = new StringBuilder(SqlTemplateManager.getCountSql(tableName));
        Pair<String, List<Object>> conditionPair = buildQuerySql(queryModel);
        List<Object> params = conditionPair.getRight();
        sql.append(conditionPair.getLeft());
        try {
            return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        } catch (EmptyResultDataAccessException e) {
            return 0L;
        } catch (Exception e) {
            log.error("count error,query:{}", JSON.toJSONString(queryModel), e);
            throw e;
        }
    }


    @Override
    public void saveAuditLog(AuditLogModel auditLogModel) {
        String tableName = calcTableName(auditLogModel.getTenant(), logTableName);
        if (auditLogModel.getId() == null && logIdGenerator != null) {
            // id 生成器存在时，使用id生成器生成id
            Long id = logIdGenerator.nextId();
            auditLogModel.setId(id);
        }
        /**
         * 记录主表和详情表
         */
        if (auditLogModel.getId() != null) {
            saveWithGeneratedId(auditLogModel, SqlTemplateManager.getInsertSqlWithId(tableName, enableLogDetailTable));
        } else {
            saveWithAutoIncrementId(auditLogModel, SqlTemplateManager.getInsertSql(tableName, enableLogDetailTable));
        }
        saveLogDetail(auditLogModel);

    }

    @Override
    public AuditLogViewModel detail(Long logId, String tenant) {
        AuditLogViewModel auditLogModel = jdbcTemplate.queryForObject(
                "select * from " + calcTableName(tenant, logTableName) + " where id=" + logId, logRowMapper);
        if (auditLogModel == null) {
            return null;
        }
        if (!enableLogDetailTable) {
            return auditLogModel;
        }
        String operateDsl = null;
        try {
            String queryDetailSql = SqlTemplateManager.getQueryDetailSql(calcTableName(tenant, logDetailTableName));
            operateDsl = jdbcTemplate.queryForObject(queryDetailSql, String.class, logId);
        } catch (EmptyResultDataAccessException e) {
            operateDsl = null;
        }
        auditLogModel.setOperateDsl(operateDsl);
        return auditLogModel;
    }

    @Override
    public ScrollQueryResult scrollQuery(ScrollQueryRequest request) {
        AuditLogQueryModel queryModel = request.getQuery();
        String tableName = calcTableName(queryModel.getTenant(), logTableName);

        // 构建查询 SQL
        StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName + " WHERE 1=1");
        Pair<String, List<Object>> whereClause = buildQuerySql(queryModel);
        List<Object> params = new ArrayList<>(whereClause.getRight());
        sql.append(whereClause.getLeft());

        // 根据方向添加游标条件（按 operate_time + id 双字段）
        Long cursorId = request.getCursorId();
        if (cursorId != null && cursorId > 0) {
            // 优先使用前端传递的游标时间，如果没有则查询数据库
            Long cursorTime;
            if (request.getCursorTime() != null) {
                cursorTime = request.getCursorTime();
            } else {
                cursorTime = getCursorTime(cursorId, tableName);
            }

            boolean isForward = "forward".equals(request.getDirection());

            // forward = 向时间更新的方向（更大的时间/ID）
            // backward = 向时间更旧的方向（更小的时间/ID）
            if (isForward) {
                sql.append(" AND (op_time > ? OR (op_time = ? AND id > ?))");
            } else {
                sql.append(" AND (op_time < ? OR (op_time = ? AND id < ?))");
            }
            params.add(cursorTime);
            params.add(cursorTime);
            params.add(cursorId);
        }

        // 后端统一按时间升序排列，保证返回的数据始终是从旧到新
        sql.append(" ORDER BY op_time ASC, id ASC");

        sql.append(" LIMIT ?");
        params.add(request.getLimit());

        // 执行查询
        List<AuditLogViewModel> list = jdbcTemplate.query(sql.toString(),
                params.toArray(), logRowMapper);

        // 设置返回结果
        ScrollQueryResult result = new ScrollQueryResult();
        result.setList(list);

        // 设置下次查询的游标（最后一条记录的 ID 和时间）
        if (!list.isEmpty()) {
            AuditLogViewModel lastLog = list.get(list.size() - 1);
            result.setNextCursor(lastLog.getId());
            result.setNextCursorTime(lastLog.getOperateTime());
        }

        return result;
    }

    /**
     * 获取游标对应的时间戳
     */
    private Long getCursorTime(Long logId, String tableName) {
        try {
            Long time = jdbcTemplate.queryForObject(
                    "SELECT op_time FROM " + tableName + " WHERE id = ?",
                    Long.class, logId);
            return time != null ? time : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }


    /**
     * 分表存储需实现
     *
     * @param tenant      租户Id/应用标识
     * @param tablePrefix 表名前缀
     * @return
     */
    protected String calcTableName(String tenant, String tablePrefix) {
        return tablePrefix;
    }


    /**
     * 使用生成的 ID（雪花算法）保存
     */
    private void saveWithGeneratedId(AuditLogModel auditLogModel, String insertSqlWithId) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSqlWithId);
            setParams(ps, auditLogModel, true);
            return ps;
        });
    }

    /**
     * 使用数据库自增主键保存
     */
    private void saveWithAutoIncrementId(AuditLogModel auditLogModel,
                                         String insertSql) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            setParams(ps, auditLogModel, false);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        auditLogModel.setId(key.longValue());
    }

    /**
     * 构建参数列表（用于单条保存）
     *
     * @param auditLogModel 日志对象
     * @param includeId     是否包含 ID
     * @return 参数列表
     */
    private List<Object> buildParams(AuditLogModel auditLogModel, boolean includeId) {
        List<Object> params = new ArrayList<>(15);
        if (includeId) {
            params.add(auditLogModel.getId());
        }
        params.add(auditLogModel.getOperator());
        params.add(auditLogModel.getBizType());
        params.add(auditLogModel.getSubType());
        params.add(auditLogModel.getOperateDesc());
        params.add(auditLogModel.getOperateTime());
        params.add(auditLogModel.getOperateObject());
        params.add(auditLogModel.getSuccess());
        params.add(auditLogModel.getTraceId());
        params.add(auditLogModel.getTenant());
        params.add(auditLogModel.getCostTime() != null ? auditLogModel.getCostTime() : 0);
        params.add(truncateError(auditLogModel.getErrorMsg()));
        params.add(auditLogModel.getOpDate());
        params.add(getObjectString(auditLogModel.getExt()));
        if (!enableLogDetailTable) {
            params.add(getObjectString(auditLogModel.getOperateDsl()));
        }
        return params;
    }

    /**
     * 设置 PreparedStatement 参数（用于单条保存）
     *
     * @param ps            PreparedStatement
     * @param auditLogModel 日志对象
     * @param includeId     是否包含 ID
     * @throws SQLException SQL 异常
     */
    private void setParams(PreparedStatement ps, AuditLogModel auditLogModel, boolean includeId) throws SQLException {
        int paramIndex = 1;
        if (includeId) {
            ps.setLong(paramIndex++, auditLogModel.getId());
        }
        ps.setString(paramIndex++, auditLogModel.getOperator());
        ps.setString(paramIndex++, auditLogModel.getBizType());
        ps.setString(paramIndex++, auditLogModel.getSubType());
        ps.setString(paramIndex++, auditLogModel.getOperateDesc());
        ps.setLong(paramIndex++, auditLogModel.getOperateTime());
        ps.setString(paramIndex++, auditLogModel.getOperateObject());
        ps.setBoolean(paramIndex++, auditLogModel.getSuccess());
        ps.setString(paramIndex++, auditLogModel.getTraceId());
        ps.setString(paramIndex++, auditLogModel.getTenant());
        ps.setLong(paramIndex++, auditLogModel.getCostTime() != null ? auditLogModel.getCostTime() : 0);
        ps.setString(paramIndex++, truncateError(auditLogModel.getErrorMsg()));
        ps.setString(paramIndex++, auditLogModel.getOpDate());
        // 对于 CLOB 字段，使用 setString 并确保正确处理 null
        String extStr = getObjectString(auditLogModel.getExt());
        if (extStr == null) {
            ps.setNull(paramIndex++, java.sql.Types.CLOB);
        } else {
            ps.setString(paramIndex++, extStr);
        }
        if (!enableLogDetailTable) {
            String dslStr = getObjectString(auditLogModel.getOperateDsl());
            if (dslStr == null) {
                ps.setNull(paramIndex, java.sql.Types.CLOB);
            } else {
                ps.setString(paramIndex, dslStr);
            }
        }
    }

    private void saveLogDetail(AuditLogModel auditLogModel) {
        if (!enableLogDetailTable || auditLogModel.getId() == null) {
            return;
        }
        String operateDsl = getObjectString(auditLogModel.getOperateDsl());
        if (operateDsl != null && !operateDsl.isEmpty()) {
            auditLogModel.setId(auditLogModel.getId());
            String insertDetailSql = SqlTemplateManager.getInsertDetailSql(calcTableName(auditLogModel.getTenant(), logDetailTableName));
            jdbcTemplate.update(insertDetailSql, auditLogModel.getId(), operateDsl);
        }
    }


    /**
     * 截断错误信息
     */
    private String truncateError(String errorMsg) {
        if (errorMsg != null && errorMsg.length() > 1000) {
            return errorMsg.substring(0, 1000);
        }
        return errorMsg;
    }

    /**
     * 构建查询 SQL 条件
     */
    protected Pair<String, List<Object>> buildQuerySql(AuditLogQueryModel queryModel) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (queryModel.getBizType() != null) {
            sql.append(" AND biz_type = ?");
            params.add(queryModel.getBizType());
        }
        if (queryModel.getSubType() != null) {
            sql.append(" AND sub_biz_type = ?");
            params.add(queryModel.getSubType());
        }
        if (queryModel.getOperator() != null) {
            sql.append(" AND operator = ?");
            params.add(queryModel.getOperator());
        }
        if (queryModel.getTenant() != null) {
            sql.append(" AND tenant = ?");
            params.add(queryModel.getTenant());
        }
        if (queryModel.getStartTime() != null) {
            sql.append(" AND op_time >= ?");
            params.add(queryModel.getStartTime());
        }
        if (queryModel.getEndTime() != null) {
            sql.append(" AND op_time <= ?");
            params.add(queryModel.getEndTime());
        }
        if (StringUtils.isNotEmpty(queryModel.getOperateDesc())) {
            sql.append(" AND op_desc like ?");
            params.add("%" + queryModel.getOperateDesc() + "%");
        }
        if (StringUtils.isNotEmpty(queryModel.getOperateObject())) {
            sql.append(" AND op_object = ?");
            params.add(queryModel.getOperateObject());
        }

        if (queryModel.getSuccess() != null) {
            sql.append(" AND success = ?");
            params.add(queryModel.getSuccess());
        }
        if (queryModel.getTraceId() != null) {
            sql.append(" AND trace_id = ?");
            params.add(queryModel.getTraceId());
        }
        return Pair.of(sql.toString(), params);
    }

    /**
     * 获取字符
     */
    protected String getObjectString(Object operateDsl) {
        if (operateDsl == null) {
            return null;
        }
        if (operateDsl instanceof String) {
            return (String) operateDsl;
        }
        return JSON.toJSONString(operateDsl);
    }

    public void setLogIdGenerator(LogIdGenerator logIdGenerator) {
        this.logIdGenerator = logIdGenerator;
    }

    /**
     * 日志映射器
     */
    protected final RowMapper<AuditLogViewModel> logRowMapper = (rs, rowNum) -> {

        Map<String, String> columnNameMap = columnNameMap(rs);
        AuditLogViewModel model = new AuditLogViewModel();
        // 非string列
        model.setOperateTime(rs.getLong(getColumnName(columnNameMap, "op_time")));
        model.setSuccess(rs.getBoolean(getColumnName(columnNameMap, "success")));
        model.setCostTime(rs.getLong(getColumnName(columnNameMap, "cost_time")));


        model.setId(getColumnStringValue(columnNameMap, "id", rs));
        model.setOperator(getColumnStringValue(columnNameMap, "operator", rs));
        model.setBizType(getColumnStringValue(columnNameMap, "biz_type", rs));
        model.setSubType(getColumnStringValue(columnNameMap, "sub_biz_type", rs));
        model.setOperateDesc(getColumnStringValue(columnNameMap, "op_desc", rs));
        model.setOperateObject(getColumnStringValue(columnNameMap, "op_object", rs));
        model.setTraceId(getColumnStringValue(columnNameMap, "trace_id", rs));
        model.setTenant(getColumnStringValue(columnNameMap, "tenant", rs));
        model.setOpDate(getColumnStringValue(columnNameMap, "op_date", rs));
        if (columnNameMap.containsKey("error_msg")) {
            model.setErrorMsg(getColumnStringValue(columnNameMap, "error_msg", rs));
        }
        if (columnNameMap.containsKey("op_dsl")) {
            model.setOperateDsl(getColumnStringValue(columnNameMap, "op_dsl", rs));
        }
        if (columnNameMap.containsKey("ext")) {
            model.setExt(getColumnStringValue(columnNameMap, "ext", rs));
        }
        return model;
    };

    private static String getColumnName(Map<String, String> columnNameMap, String columnNameLower) {
        return columnNameMap.get(columnNameLower);
    }

    private static String getColumnStringValue(Map<String, String> columnNameMap, String columnNameLower, ResultSet rs) throws SQLException {
        return rs.getString(columnNameMap.get(columnNameLower));
    }

    private static Map<String, String> columnNameMap(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        Map<String, String> columnNameMap = new HashMap<>();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            columnNameMap.put(columnName.toLowerCase(), columnName);
        }
        return columnNameMap;
    }

    @Override
    public void batchSaveAuditLog(List<AuditLogModel> auditLogModels) {

        if (auditLogModels == null || auditLogModels.isEmpty()) {
            return;
        }
        // 确保所有日志都有 ID（批量插入时必须要有 ID）
        ensureIdsGenerated(auditLogModels);
        if (auditLogModels.size() == 1) {
            saveAuditLog(auditLogModels.get(0));
            return;
        }
        try {
            // 按租户分组（分表场景）
            auditLogModels.stream().collect(Collectors.groupingBy(AuditLogModel::getTenant)).forEach((tenant, logs) -> {
                // 逐租户批量插入
                String tableName = calcTableName(tenant, logTableName);
                // 批量插入主表
                batchInsertMainTable(tableName, logs);

                // 批量插入详情表（如果启用）
                if (enableLogDetailTable) {
                    batchInsertDetailTable(tenant, logs);
                }
            });

        } catch (Exception e) {
            log.error("Batch save audit log error, size: {}", auditLogModels.size(), e);
            throw e;
        }
    }

    /**
     * 确保所有日志都有 ID
     * <p>批量插入时，如果使用数据库自增 ID，无法高效返回生成的 ID，会导致详情表插入失败</p>
     * <p>因此批量保存时强制使用 ID 生成器（雪花算法）</p>
     */
    private void ensureIdsGenerated(List<AuditLogModel> auditLogModels) {
        if (logIdGenerator != null) {
            for (AuditLogModel model : auditLogModels) {
                if (model.getId() == null) {
                    model.setId(logIdGenerator.nextId());
                }
            }
        } else {
            // 如果没有配置 ID 生成器，记录警告（可能导致详情表插入失败）
            log.warn("Batch save without ID generator configured. If detail table is enabled, insert may fail.");
        }
    }

    /**
     * 批量插入主表
     */
    private void batchInsertMainTable(String tableName, List<AuditLogModel> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        String sql = SqlTemplateManager.getInsertSqlWithId(tableName, enableLogDetailTable);
        jdbcTemplate.batchUpdate(sql, logs, logs.size(), (ps, log) -> {
            try {
                setParams(ps, log, true);
            } catch (SQLException e) {
                throw new RuntimeException("Set batch params error", e);
            }
        });
    }

    /**
     * 批量插入详情表
     */
    private void batchInsertDetailTable(String tenant, List<AuditLogModel> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        String detailTableName = calcTableName(tenant, logDetailTableName);
        String insertDetailSql = SqlTemplateManager.getInsertDetailSql(detailTableName);

        // 过滤出有详情的日志
        List<AuditLogModel> logsWithDetail = new ArrayList<>();
        for (AuditLogModel log : logs) {
            String operateDsl = getObjectString(log.getOperateDsl());
            if (operateDsl != null && !operateDsl.isEmpty()) {
                logsWithDetail.add(log);
            }
        }

        if (logsWithDetail.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(insertDetailSql, logsWithDetail, logsWithDetail.size(), (ps, log) -> {
            ps.setLong(1, log.getId());
            String dslStr = getObjectString(log.getOperateDsl());
            if (dslStr == null) {
                ps.setNull(2, java.sql.Types.CLOB);
            } else {
                ps.setString(2, dslStr);
            }
        });
    }
}