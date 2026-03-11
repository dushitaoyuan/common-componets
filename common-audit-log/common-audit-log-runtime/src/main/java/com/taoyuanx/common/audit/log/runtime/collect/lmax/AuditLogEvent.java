package com.taoyuanx.common.audit.log.runtime.collect.lmax;

import com.taoyuanx.common.audit.log.model.AuditLogModel;

/**
 * LMAX Disruptor 事件类
 *
 * @author taoyuan
 * @date 2025/3/11
 */
public class AuditLogEvent {
    private AuditLogModel auditLog;

    public AuditLogModel getAuditLog() {
        return auditLog;
    }

    public void setAuditLog(AuditLogModel auditLog) {
        this.auditLog = auditLog;
    }
}