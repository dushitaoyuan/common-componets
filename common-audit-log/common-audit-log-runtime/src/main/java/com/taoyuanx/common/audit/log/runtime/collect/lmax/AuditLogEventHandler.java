package com.taoyuanx.common.audit.log.runtime.collect.lmax;

import com.lmax.disruptor.EventHandler;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;

/**
 * LMAX Disruptor 事件处理器
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogEventHandler implements EventHandler<AuditLogEvent> {
    private final AuditLogService auditLogService;
    private final AuditLogModelPool auditLogModelPool;

    public AuditLogEventHandler(AuditLogService auditLogService, AuditLogModelPool auditLogModelPool) {
        this.auditLogService = auditLogService;
        this.auditLogModelPool = auditLogModelPool;
    }

    public AuditLogEventHandler(AuditLogService auditLogService) {
        this(auditLogService, null);
    }

    @Override
    public void onEvent(AuditLogEvent event, long sequence, boolean endOfBatch) {
        AuditLogModel auditLog = null;
        try {
            auditLog = event.getAuditLog();
            if (auditLog != null) {
                auditLogService.saveAuditLog(auditLog);
            }
        } catch (Exception e) {
            log.error("Error processing audit log event: {}", auditLog, e);
        } finally {
            if (auditLogModelPool != null) {
                auditLogModelPool.returnObject(auditLog);
            }
            event.setAuditLog(null);
        }
    }
}