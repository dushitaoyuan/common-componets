package com.taoyuanx.common.audit.log.runtime.collect;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.aop.AuditLogMethodInterceptor;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogException;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 异步内存队列搜集（支持批量保存）
 *
 * @author taoyuan
 * @date 2025/7/29 18:19
 */
@Slf4j
public class AuditLogAsyncCollector implements AuditLogCollector {
    private AuditLogStoreService auditLogService;
    /**
     * 收集线程收集间隔时间
     */
    private int collectInterval = 50;
    /**
     * 收集队列满时的等待时间，默认休眠2s,负数则 入队失败丢弃
     */
    private int queueFullWaitTime = 2000;
    private static ArrayBlockingQueue<AuditLogModel> logQueue;

    private static final int DEFAULT_LOG_QUEUE_SIZE = 1000;

    private static final int DEFAULT_LOG_COLLECT_INTERVAL = 50;
    private static LogCollectThread logCollectThread = null;

    private volatile boolean started = false;

    private AuditLogModelPool auditLogModelPool;

    // 批量保存配置
    private final boolean batchEnabled;
    private final int batchSize;
    private final long batchMaxWaitTime;


    public AuditLogAsyncCollector(AuditLogStoreService auditLogService, Integer logQueueSize, Integer collectInterval,
                                  Integer queueFullWaitTime, AuditLogModelPool auditLogModelPool,
                                  Boolean batchEnabled, Integer batchSize, Long batchMaxWaitTime) {
        this.auditLogService = auditLogService;
        this.collectInterval = collectInterval == null ? DEFAULT_LOG_COLLECT_INTERVAL : collectInterval;
        this.queueFullWaitTime = queueFullWaitTime == null ? 2000 : queueFullWaitTime;
        this.auditLogModelPool = auditLogModelPool;
        this.batchEnabled = batchEnabled != null && batchEnabled;
        this.batchSize = batchSize != null && batchSize > 0 ? batchSize : 100;
        this.batchMaxWaitTime = batchMaxWaitTime != null && batchMaxWaitTime > 0 ? batchMaxWaitTime : 1000L;

        synchronized (AuditLogMethodInterceptor.class) {
            if (logQueue == null) {
                logQueue = new ArrayBlockingQueue<>(logQueueSize == null ? DEFAULT_LOG_QUEUE_SIZE : logQueueSize);
            }
            if (logCollectThread == null) {
                logCollectThread = new LogCollectThread();
                logCollectThread.start();
                started = true;
            }
        }
    }

    public AuditLogAsyncCollector(AuditLogStoreService auditLogService, AuditLogModelPool auditLogModelPool) {
        this(auditLogService, DEFAULT_LOG_QUEUE_SIZE, DEFAULT_LOG_COLLECT_INTERVAL, -1, auditLogModelPool,
                true, 100, 1000L);
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        if (!started) {
            throw new IllegalStateException("AuditLogAsyncCollector 未启动");
        }

        boolean offered;
        if (queueFullWaitTime > 0) {
            offered = logQueue.offer(auditLogModel, queueFullWaitTime, TimeUnit.MILLISECONDS);
        } else {
            offered = logQueue.offer(auditLogModel);
        }

        if (!offered) {
            log.error("collectAuditLogModel async add logQueue error, logModel:{}", JSON.toJSONString(auditLogModel));
            throw new LogException("async collect log error");
        }

        // 关键优化: 成功入队后立即归还对象到池中,减少对象占用时间
        if (auditLogModelPool != null) {
            auditLogModelPool.returnObject(auditLogModel);
        }
    }

    @Override
    public void close() {
        started = false;
        logCollectThread = null;
        if (auditLogModelPool != null) {
            auditLogModelPool.close();
        }

    }


    private class LogCollectThread extends Thread {
        // 批量相关字段（仅 batchEnabled=true 时有效）
        private List<AuditLogModel> batchBuffer;
        private long lastFlushTime;

        public LogCollectThread() {
            super("AsyncLogCollectThread");
            // 根据配置初始化批量缓冲
            if (batchEnabled) {
                this.batchBuffer = new ArrayList<>(batchSize);
                this.lastFlushTime = System.currentTimeMillis();
            }
        }

        @Override
        public void run() {
            try {
                while (started) {
                    AuditLogModel auditLog = logQueue.poll(collectInterval, TimeUnit.MILLISECONDS);
                    if (batchEnabled) {
                        // ✅ 开启批量：累积到缓冲区
                        handleBatchMode(auditLog);
                    } else {
                        // ❌ 未开启批量：直接保存
                        handleSingleMode(auditLog);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while collecting log,logQueueSize:{}", logQueue.size(), e);
            } catch (Exception e) {
                log.error("Error while collecting log,logQueueSize:{}", logQueue.size(), e);
            }
        }

        /**
         * 批量模式处理
         */
        private void handleBatchMode(AuditLogModel auditLog) {
            if (auditLog != null) {
                AuditLogModel copy = AuditLogUtil.cloneAuditLog(auditLog);
                batchBuffer.add(copy);
            }

            // 判断是否触发批量保存
            boolean shouldFlush = batchBuffer.size() >= batchSize
                    || (System.currentTimeMillis() - lastFlushTime > batchMaxWaitTime
                    && !batchBuffer.isEmpty());

            if (shouldFlush && !batchBuffer.isEmpty()) {
                doBatchSave(new ArrayList<>(batchBuffer));
                batchBuffer.clear();
                lastFlushTime = System.currentTimeMillis();
            }
        }

        /**
         * 单条模式处理
         */
        private void handleSingleMode(AuditLogModel auditLog) {
            if (auditLog == null) {
                return;
            }
            AuditLogModel copy = AuditLogUtil.cloneAuditLog(auditLog);
            try {
                auditLogService.saveAuditLog(copy);
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
                log.error("Batch save error, size: {}, fallback to single save", logs.size(), e);
                fallbackSingleSave(logs);
            } finally {
                if (auditLogModelPool != null) {
                    auditLogModelPool.returnObjects(logs);
                }
            }
        }

        /**
         * 降级策略：批量失败后逐条保存
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
}
