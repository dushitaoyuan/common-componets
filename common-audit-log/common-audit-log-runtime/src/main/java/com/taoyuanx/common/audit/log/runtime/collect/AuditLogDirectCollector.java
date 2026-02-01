package com.taoyuanx.common.audit.log.runtime.collect;

import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.service.AuditLogService;

/**
 * 直接收集
 *
 * @author taoyuan
 * @date 2025/7/29 18:19
 */
public class AuditLogDirectCollector implements AuditLogCollector {
    private AuditLogService auditLogService;

    public AuditLogDirectCollector(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        auditLogService.saveAuditLog(auditLogModel);
    }
}
