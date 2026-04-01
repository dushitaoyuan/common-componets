package com.taoyuanx.common.audit.log.runtime.impl;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.common.LogIdGenerator;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.ext.AuditLogQueryService;
import com.taoyuanx.common.audit.log.runtime.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.runtime.model.PageModel;
import com.taoyuanx.common.audit.log.runtime.autoconfigure.AuditLogProperties;
import com.taoyuanx.common.audit.log.runtime.model.PageQueryModel;
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
import java.util.Date;

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
        this.logIdGenerator=logIdGenerator;
    }


    @Override
    public PageModel<AuditLogModel> page(PageQueryModel<AuditLogQueryModel> pageQuery) {
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
        PageModel<AuditLogModel> pageModel = new PageModel<>();
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
        }catch (Exception e){
            log.error("page error,query:{}",JSON.toJSONString(queryModel),e);
            throw  e;
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
        }catch (Exception e){
            log.error("count error,query:{}",JSON.toJSONString(queryModel),e);
            throw  e;
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
    public AuditLogModel detail(Long logId, String tenant) {
        AuditLogModel auditLogModel = jdbcTemplate.queryForObject(
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
        List<Object> params = new ArrayList<>(15);
        params.add(auditLogModel.getId());
        params.add(auditLogModel.getOperator());
        params.add(auditLogModel.getBizType());
        params.add(auditLogModel.getSubType());
        params.add(auditLogModel.getOperateDesc());
        params.add(new Timestamp(auditLogModel.getOperateTime().getTime()));
        params.add(auditLogModel.getOperateObject());
        params.add(auditLogModel.getSuccess());
        params.add(auditLogModel.getTraceId());
        params.add(auditLogModel.getTenant());
        params.add(auditLogModel.getCostTime() != null ? auditLogModel.getCostTime() : 0);
        params.add(truncateError(auditLogModel.getErrorMsg()));
        params.add(auditLogModel.getOpDate());
        params.add(auditLogModel.getExt() == null ? null : JSON.toJSONString(auditLogModel.getExt()));
        if (!enableLogDetailTable) {
            params.add(auditLogModel.getOperateDsl());
        }
        jdbcTemplate.update(insertSqlWithId, params.toArray());

    }

    /**
     * 使用数据库自增主键保存
     */
    private void saveWithAutoIncrementId(AuditLogModel auditLogModel,
                                         String insertSql) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, auditLogModel.getOperator());
            ps.setString(2, auditLogModel.getBizType());
            ps.setString(3, auditLogModel.getSubType());
            ps.setString(4, auditLogModel.getOperateDesc());
            ps.setTimestamp(5, new Timestamp(auditLogModel.getOperateTime().getTime()));
            ps.setString(6, auditLogModel.getOperateObject());
            ps.setBoolean(7, auditLogModel.getSuccess());
            ps.setString(8, auditLogModel.getTraceId());
            ps.setString(9, auditLogModel.getTenant());
            ps.setLong(10, auditLogModel.getCostTime() != null ? auditLogModel.getCostTime() : 0);
            ps.setString(11, truncateError(auditLogModel.getErrorMsg()));
            ps.setString(12, auditLogModel.getOpDate());
            ps.setString(13, getObjectString(auditLogModel.getExt()));
            if (!enableLogDetailTable) {
                ps.setString(14, getObjectString(auditLogModel.getOperateDsl()));
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        auditLogModel.setId(key.longValue());
    }

    private void saveLogDetail(AuditLogModel auditLogModel) {
        if (!enableLogDetailTable || auditLogModel.getId() == null) {
            return;
        }
        String operateDsl = getObjectString(auditLogModel.getOperateDsl());
        if (operateDsl != null && operateDsl.length() > 0) {
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
            params.add(new Timestamp(queryModel.getStartTime().getTime()));
        }
        if (queryModel.getEndTime() != null) {
            sql.append(" AND op_time <= ?");
            params.add(new Timestamp(queryModel.getEndTime().getTime()));
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
    protected final RowMapper<AuditLogModel> logRowMapper = (rs, rowNum) -> {

        Map<String, String> columnNameMap = columnNameMap(rs);
        AuditLogModel model = new AuditLogModel();
        // 非string列
        model.setOperateTime(new Date(rs.getTimestamp(getColumnName(columnNameMap, "op_time")).getTime()));
        model.setSuccess(rs.getBoolean(getColumnName(columnNameMap, "success")));
        model.setCostTime(rs.getLong(getColumnName(columnNameMap, "cost_time")));
        model.setId(rs.getLong(getColumnName(columnNameMap, "id")));


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
}