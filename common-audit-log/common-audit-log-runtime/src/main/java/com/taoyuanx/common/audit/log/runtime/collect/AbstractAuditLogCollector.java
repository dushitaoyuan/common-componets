package com.taoyuanx.common.audit.log.runtime.collect;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.fallback.LocalFileFallbackWriter;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * 审计日志收集器抽象基类
 * 提供统一的降级逻辑，确保在对象归还到池之前执行降级
 *
 * @author taoyuan
 * @date 2026-04-18
 */
@Slf4j
public abstract class AbstractAuditLogCollector implements AuditLogCollector {
    
    protected final AuditLogStoreService auditLogService;
    protected final AuditLogModelPool auditLogModelPool;
    protected final LocalFileFallbackWriter fallbackWriter;
    
    public AbstractAuditLogCollector(AuditLogStoreService auditLogService,
                                     AuditLogModelPool auditLogModelPool,
                                     LocalFileFallbackWriter fallbackWriter) {
        this.auditLogService = auditLogService;
        this.auditLogModelPool = auditLogModelPool;
        this.fallbackWriter = fallbackWriter;
    }
    
    /**
     * 统一的降级方法（单条）
     * 在对象归还前调用，确保降级时对象数据完整
     *
     * @param model 日志模型
     * @param saveAction 保存操作
     */
    public void fallbackIfFailed(AuditLogModel model, Runnable saveAction) {
        try {
            saveAction.run();
        } catch (Throwable e) {
            if(model!=null){
                autoFallBack(Arrays.asList(model),e);
            }
        }
    }
    
    /**
     * 批量降级方法
     *
     * @param models 日志模型列表
     * @param saveAction 批量保存操作
     */
    public void fallbackBatchIfFailed(List<AuditLogModel> models, Runnable saveAction) {
        if (models == null || models.isEmpty()) {
            return;
        }
        
        try {
            saveAction.run();
        } catch (Throwable e) {
            autoFallBack(models,e);
        }
    }

    private void autoFallBack(List<AuditLogModel> models, Throwable e) {
        if (fallbackWriter == null) {
            log.warn("No fallback writer configured, log lost,logContent: {}", JSON.toJSONString(models));
            return;
        }
        fallbackWriter.writeBatch(models);

    }
    
    /**
     * 获取 AuditLogService（供子类使用）
     */
    public AuditLogStoreService getAuditLogService() {
        return auditLogService;
    }

    /**
     * 获取对象池（供子类使用）
     */
    public AuditLogModelPool getAuditLogModelPool() {
        return auditLogModelPool;
    }
}
