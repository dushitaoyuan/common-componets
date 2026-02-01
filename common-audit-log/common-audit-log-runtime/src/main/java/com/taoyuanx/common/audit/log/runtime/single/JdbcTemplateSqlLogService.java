package com.taoyuanx.common.audit.log.runtime.single;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.model.PageModel;
import com.taoyuanx.common.audit.log.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/5/19 20:44
 */
@RequiredArgsConstructor
public class JdbcTemplateSqlLogService implements AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    // 插入日志的 SQL
    private static final String INSERT_SQL = "INSERT INTO op_log ( operator, biz_type, sub_biz_type, op_desc, op_time, op_object, success,trace_id, tenant, cost_time, error_msg, op_date, ext) VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";


    // 写入日志详情
    private static final String INSERT_DETAIL_SQL = "INSERT INTO op_log_detail (op_log_id,op_dsl) VALUES (?,?)";

    // 查询 SQL 模板（支持动态条件）
    private static final String QUERY_SQL_BASE = "SELECT id, operator, biz_type, sub_biz_type, op_desc, op_time, op_object, trace_id, tenant,success, cost_time, error_msg, op_date, ext FROM op_log WHERE 1=1";

    private static final String QUERY_COUNT_SQL_BASE = "SELECT count(*) FROM op_log WHERE 1=1";

    // 日志映射器
    private final RowMapper<AuditLogModel> logRowMapper = (rs, rowNum) -> {
        AuditLogModel model = new AuditLogModel();
        model.setId(rs.getLong("id"));
        model.setOperator(rs.getString("operator"));
        model.setBizType(rs.getString("biz_type"));
        model.setSubType(rs.getString("sub_biz_type"));
        model.setOperateDesc(rs.getString("op_desc"));
        model.setOperateTime(new Date(rs.getTimestamp("op_time").getTime()));
        model.setOperateObject(rs.getString("op_object"));
        model.setTraceId(rs.getString("trace_id"));
        model.setTenant(rs.getString("tenant"));
        model.setSuccess(rs.getBoolean("success"));
        model.setCostTime(rs.getLong("cost_time"));
        model.setErrorMsg(rs.getString("error_msg"));
        model.setOpDate(rs.getString("op_date"));
        model.setExt(rs.getString("ext"));
        return model;
    };

    @Override
    public PageModel<AuditLogModel> page(AuditLogQueryModel queryModel) {
        StringBuilder sql = new StringBuilder(QUERY_SQL_BASE);
        Pair<String, List<Object>> conditionPair = buildQuerySql(queryModel);
        sql.append(conditionPair.getLeft());
        // 分页处理（SQLite 使用 LIMIT 和 OFFSET）
        sql.append(" ORDER BY op_time DESC LIMIT ? OFFSET ?");
        List<Object> params = conditionPair.getRight();
        params.add(queryModel.getPageSize());
        params.add((queryModel.getPageNum() - 1) * queryModel.getPageSize());
        PageModel<AuditLogModel> pageModel = new PageModel<>();
        pageModel.setTotal(0L);
        pageModel.setList(Collections.emptyList());
        try {
            Long count = count(queryModel);
            if (count <= 0) {
                return pageModel;
            }
            pageModel.setTotal(count);
            pageModel.setList(jdbcTemplate.query(sql.toString(), logRowMapper, params.toArray()));
            return pageModel;
        } catch (EmptyResultDataAccessException e) {
            return pageModel;
        }
    }

    @Override
    public AuditLogModel detail(Long logId) {
        AuditLogModel auditLogModel = jdbcTemplate.queryForObject("select * from op_log where id=" + logId, logRowMapper);
        if (auditLogModel == null) {
            return null;
        }
        String operateDsl = null;
        try {
            operateDsl = jdbcTemplate.queryForObject("select op_dsl from op_log_detail where op_log_id= ?", String.class, logId);
        } catch (EmptyResultDataAccessException e) {
            operateDsl = null;
        }
        auditLogModel.setOperateDsl(operateDsl);
        return auditLogModel;
    }

    private Long count(AuditLogQueryModel queryModel) {
        StringBuilder sql = new StringBuilder(QUERY_COUNT_SQL_BASE);
        Pair<String, List<Object>> conditionPair = buildQuerySql(queryModel);
        List<Object> params = conditionPair.getRight();
        sql.append(conditionPair.getLeft());
        try {
            return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        } catch (EmptyResultDataAccessException e) {
            return 0L;
        }
    }

    private Pair<String, List<Object>> buildQuerySql(AuditLogQueryModel queryModel) {
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

    @Override
    public void saveAuditLog(AuditLogModel auditLogModel) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
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
            String errorMsg = auditLogModel.getErrorMsg();
            if (errorMsg != null && errorMsg.length() > 1000) {
                errorMsg = errorMsg.substring(0, 1000);
            }
            ps.setString(11, errorMsg);
            ps.setString(12, auditLogModel.getOpDate());
            ps.setString(13, auditLogModel.getExt() == null ? null : JSON.toJSONString(auditLogModel.getExt()));
            return ps;
        }, keyHolder);
        String operateDsl = getOperateDsl(auditLogModel.getOperateDsl());
        if (operateDsl == null || operateDsl.length() == 0) {
            return;
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            auditLogModel.setId(key.longValue());
            jdbcTemplate.update(INSERT_DETAIL_SQL, auditLogModel.getId(), operateDsl);
        }
    }

    private String getOperateDsl(Object operateDsl) {
        if (operateDsl == null) {
            return null;
        }
        if (operateDsl instanceof String) {
            return (String) operateDsl;
        }
        return JSON.toJSONString(operateDsl);

    }


}
