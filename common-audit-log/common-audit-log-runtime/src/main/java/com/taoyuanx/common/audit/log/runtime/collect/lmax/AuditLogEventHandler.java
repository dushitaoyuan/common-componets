package com.taoyuanx.common.audit.log.runtime.collect.lmax;

import com.lmax.disruptor.EventHandler;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * LMAX Disruptor 事件处理器（支持批量保存）
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogEventHandler implements EventHandler<AuditLogEvent> {
    private final AuditLogStoreService auditLogService;
    private final AuditLogModelPool auditLogModelPool;
    private final boolean batchEnabled;
    
    // 批量相关字段（仅 batchEnabled=true 时有效）
    private final List<AuditLogModel> batchBuffer;
    private final int batchSize;
    
    public AuditLogEventHandler(AuditLogStoreService auditLogService, AuditLogModelPool auditLogModelPool,
                                boolean batchEnabled, int batchSize) {
        this.auditLogService = auditLogService;
        this.auditLogModelPool = auditLogModelPool;
        this.batchEnabled = batchEnabled;
        this.batchSize = batchSize;
        this.batchBuffer = batchEnabled ? new ArrayList<>(batchSize) : null;
    }

    public AuditLogEventHandler(AuditLogStoreService auditLogService, AuditLogModelPool auditLogModelPool) {
        this(auditLogService, auditLogModelPool, true, 100);
    }

    public AuditLogEventHandler(AuditLogStoreService auditLogService) {
        this(auditLogService, null);
    }

    @Override
    public void onEvent(AuditLogEvent event, long sequence, boolean endOfBatch) {
        AuditLogModel original = event.getAuditLog();
        
        try {
            AuditLogModel copyForSave = AuditLogUtil.cloneAuditLog(original);
            if (batchEnabled) {
                // 开启批量：累积到缓冲区
                handleBatchMode(copyForSave, endOfBatch);
            } else {
                // 未开启批量：直接保存
                handleSingleMode(copyForSave);
            }
        } catch (Exception e) {
            log.error("Disruptor collect error", e);
        } finally {
            if (original != null && auditLogModelPool != null) {
                auditLogModelPool.returnObject(original);
            }
            event.setAuditLog(null);
        }
    }
    
    /**
     * 批量模式处理
     */
    private void handleBatchMode(AuditLogModel auditLog, boolean endOfBatch) {
        if (auditLog != null) {
            batchBuffer.add(auditLog);
        }
        
        // 触发条件：endOfBatch 或 达到批量大小
        boolean shouldFlush = endOfBatch || batchBuffer.size() >= batchSize;
        
        if (shouldFlush && !batchBuffer.isEmpty()) {
            doBatchSave(new ArrayList<>(batchBuffer));
            batchBuffer.clear();
        }
    }
    
    /**
     * 单条模式处理
     */
    private void handleSingleMode(AuditLogModel auditLog) {
        if (auditLog == null) {
            return;
        }
        
        try {
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Single save error", e);
        } finally {
            if (auditLogModelPool != null) {
                auditLogModelPool.returnObject(auditLog);
            }
        }
    }
    
    /**
     * 批量保存（统一入口）
     */
    private void doBatchSave(List<AuditLogModel> logs) {
        try {
            auditLogService.batchSaveAuditLog(logs);
        } catch (Exception e) {
            log.error("Disruptor batch save error, size: {}", logs.size(), e);
            fallbackSingleSave(logs);
        } finally {
            if (auditLogModelPool != null) {
                auditLogModelPool.returnObjects(logs);
            }
        }
    }
    
    /**
     * 降级策略：批量失败后逐条保存
     * 注意：对象归还在doBatchSave的finally中统一处理，这里不要归还
     */
    private void fallbackSingleSave(List<AuditLogModel> logs) {
        for (AuditLogModel auditLogModel : logs) {
            try {
                auditLogService.saveAuditLog(auditLogModel);
            } catch (Exception ex) {
                log.error("Fallback single save error, logId: {}", auditLogModel.getId(), ex);
            }
        }
    }

}