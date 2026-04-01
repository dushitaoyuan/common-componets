package com.taoyuanx.common.audit.log.runtime.collect.lmax;

import com.lmax.disruptor.EventHandler;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;

/**
 * LMAX Disruptor 事件处理器
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogEventHandler implements EventHandler<AuditLogEvent> {
    private final AuditLogStoreService auditLogService;
    private final AuditLogModelPool auditLogModelPool;

    public AuditLogEventHandler(AuditLogStoreService auditLogService, AuditLogModelPool auditLogModelPool) {
        this.auditLogService = auditLogService;
        this.auditLogModelPool = auditLogModelPool;
    }

    public AuditLogEventHandler(AuditLogStoreService auditLogService) {
        this(auditLogService, null);
    }

    @Override
    public void onEvent(AuditLogEvent event, long sequence, boolean endOfBatch) {
        AuditLogModel auditLog = event.getAuditLog();
        try {
            if (auditLog != null) {
                auditLogService.saveAuditLog(auditLog);
            }
        } catch (Exception e) {
            log.error("disruptor doCollect error auditLog:{}", auditLog, e);
        } finally {
            event.setAuditLog(null);
            if (auditLogModelPool != null && auditLog != null) {
                auditLogModelPool.returnObject(auditLog);
            }
        }
    }
}