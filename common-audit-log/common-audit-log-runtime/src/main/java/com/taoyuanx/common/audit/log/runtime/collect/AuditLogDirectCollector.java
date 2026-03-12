package com.taoyuanx.common.audit.log.runtime.collect;

import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogService;

/**
 * 直接收集
 *
 * @author taoyuan
 * @date 2025/7/29 18:19
 */
public class AuditLogDirectCollector implements AuditLogCollector {
    private AuditLogService auditLogService;
    private AuditLogModelPool auditLogModelPool;


    public AuditLogDirectCollector(AuditLogService auditLogService, AuditLogModelPool auditLogModelPool) {
        this.auditLogService = auditLogService;
        this.auditLogModelPool = auditLogModelPool;
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        try {
            auditLogService.saveAuditLog(auditLogModel);
        } finally {
            if (auditLogModelPool != null) {
                auditLogModelPool.returnObject(auditLogModel);
            }
        }
    }

    @Override
    public void close() {
        if(auditLogModelPool!=null){
            auditLogModelPool.close();
        }

    }
}
