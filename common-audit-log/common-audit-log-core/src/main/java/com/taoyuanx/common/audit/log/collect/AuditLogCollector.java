package com.taoyuanx.common.audit.log.collect;

import com.taoyuanx.common.audit.log.model.AuditLogModel;

/**
 * 日志收集器
 *
 * @author taoyuan
 * @date 2025/7/29 18:17
 */
public interface AuditLogCollector {
    void collect(AuditLogModel auditLogModel) throws Exception;

    default void close() {
    }
}
