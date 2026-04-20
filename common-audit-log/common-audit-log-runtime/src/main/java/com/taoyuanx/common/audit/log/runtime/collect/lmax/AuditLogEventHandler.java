package com.taoyuanx.common.audit.log.runtime.collect.lmax;

import com.lmax.disruptor.EventHandler;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.collect.AbstractAuditLogCollector;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LMAX Disruptor 事件处理器（支持批量保存）
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogEventHandler implements EventHandler<AuditLogEvent> {
    private final AbstractAuditLogCollector collector;
    private final boolean batchEnabled;
    
    // 批量相关字段（仅 batchEnabled=true 时有效）
    private final List<AuditLogModel> batchBuffer;
    private final int batchSize;
    
    public AuditLogEventHandler(AbstractAuditLogCollector collector,
                                boolean batchEnabled, int batchSize) {
        this.collector = collector;
        this.batchEnabled = batchEnabled;
        this.batchSize = batchSize;
        this.batchBuffer = batchEnabled ? new ArrayList<>(batchSize) : null;
    }

    @Override
    public void onEvent(AuditLogEvent event, long sequence, boolean endOfBatch) {
        AuditLogModel original = event.getAuditLog();
        
        try {
            AuditLogModel copy = AuditLogUtil.cloneAuditLog(original);
            if (batchEnabled) {
                handleBatchMode(copy, endOfBatch);
            } else {
                handleSingleMode(copy);
            }
        } catch (Exception e) {
            log.error("EventHandler error", e);
        } finally {
            // 归还在最后
            if (original != null && collector.getAuditLogModelPool() != null) {
                collector.getAuditLogModelPool().returnObject(original);
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
    private void handleSingleMode(AuditLogModel copy) {
        if (copy == null) {
            return;
        }
        collector.fallbackIfFailed(copy, () -> {
                collector.getAuditLogService().saveAuditLog(copy);
        });
        if (copy != null && collector.getAuditLogModelPool() != null) {
            collector.getAuditLogModelPool().returnObject(copy);
        }
    }
    
    /**
     *
     */
    private void doBatchSave(List<AuditLogModel> logs) {
        collector.fallbackBatchIfFailed(logs, () -> {
                collector.getAuditLogService().batchSaveAuditLog(logs);
        });
        if (logs != null && collector.getAuditLogModelPool() != null) {
            collector.getAuditLogModelPool().returnObjects(logs);
        }
    }

}