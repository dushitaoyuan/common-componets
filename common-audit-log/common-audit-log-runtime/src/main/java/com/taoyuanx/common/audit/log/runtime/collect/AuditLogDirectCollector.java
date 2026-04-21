package com.taoyuanx.common.audit.log.runtime.collect;

import com.taoyuanx.common.audit.log.fallback.FallBackWriter;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;

/**
 * 直接收集器（同步保存）
 *
 * @author taoyuan
 * @date 2025/7/29 18:19
 */
public class AuditLogDirectCollector extends AbstractAuditLogCollector {

    public AuditLogDirectCollector(AuditLogStoreService auditLogService,
                                   AuditLogModelPool auditLogModelPool,
                                   FallBackWriter fallbackWriter) {
        super(auditLogService, auditLogModelPool, fallbackWriter);
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        fallbackIfFailed(auditLogModel, () -> {
            auditLogService.saveAuditLog(auditLogModel);
        });
        if (auditLogModelPool != null) {
            auditLogModelPool.returnObject(auditLogModel);
        }
    }
}
