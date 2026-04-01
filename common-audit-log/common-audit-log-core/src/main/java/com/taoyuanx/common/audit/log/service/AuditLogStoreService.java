package com.taoyuanx.common.audit.log.service;

import com.taoyuanx.common.audit.log.model.AuditLogModel;

/**
 * 日志操作服务
 *
 * @author taoyuan
 * @date 2025/5/19 20:39
 */
public interface AuditLogStoreService {
    /**
     * 日志保存
     * @param auditLogModel
     */
    void saveAuditLog(AuditLogModel auditLogModel);
}
