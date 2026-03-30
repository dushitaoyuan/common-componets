package com.taoyuanx.common.audit.log.runtime.impl.log;

import com.taoyuanx.common.audit.log.runtime.autoconfigure.AuditLogProperties;
import com.taoyuanx.common.audit.log.runtime.impl.AbstractJdbcTemplateAuditLogService;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 单表审计日志服务实现
 * <p>
 * 使用单表存储审计日志，不进行分表
 * </p>
 *
 * @author taoyuan
 * @date 2025/7/29
 */
public class SingleTableAuditLogService extends AbstractJdbcTemplateAuditLogService {


    public SingleTableAuditLogService(JdbcTemplate jdbcTemplate, AuditLogProperties auditLogProperties) {
        super(jdbcTemplate, auditLogProperties);
    }



}