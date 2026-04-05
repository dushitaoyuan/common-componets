package com.taoyuanx.common.audit.log.service;

import com.taoyuanx.common.audit.log.model.AuditLogModel;

import java.util.List;

/**
 * 日志操作服务
 *
 * @author taoyuan
 * @date 2025/5/19 20:39
 */
public interface AuditLogStoreService {
    /**
     * 日志保存
     *
     * @param auditLogModel
     */
    void saveAuditLog(AuditLogModel auditLogModel);

    /**
     * 批量保存日志
     * <p>默认实现：逐条保存（向后兼容）</p>
     *
     * @param auditLogModels 日志列表
     */
    default void batchSaveAuditLog(List<AuditLogModel> auditLogModels) {
        if (auditLogModels == null || auditLogModels.isEmpty()) {
            return;
        }
        if (auditLogModels.size() == 1) {
            saveAuditLog(auditLogModels.get(0));
            return;
        }
        for (AuditLogModel model : auditLogModels) {
            saveAuditLog(model);
        }
    }
}
