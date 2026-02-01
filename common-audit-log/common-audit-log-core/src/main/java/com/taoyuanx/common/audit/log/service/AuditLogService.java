package com.taoyuanx.common.audit.log.service;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.model.PageModel;

import java.util.List;

/**
 * 日志操作服务
 *
 * @author taoyuan
 * @date 2025/5/19 20:39
 */
public interface AuditLogService {
    void saveAuditLog(AuditLogModel auditLogModel);

    PageModel<AuditLogModel> page(AuditLogQueryModel queryModel);

    AuditLogModel detail(Long logId);
}
