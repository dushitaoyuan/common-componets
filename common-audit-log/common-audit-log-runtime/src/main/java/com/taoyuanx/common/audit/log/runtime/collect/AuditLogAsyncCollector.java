package com.taoyuanx.common.audit.log.runtime.collect;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.fallback.LocalFileFallbackWriter;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import com.alibaba.fastjson2.JSON;
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
public class AuditLogAsyncCollector extends AbstractAuditLogCollector {
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

    // 批量保存配置
    private final boolean batchEnabled;
    private final int batchSize;
    private final long batchMaxWaitTime;


    public AuditLogAsyncCollector(AuditLogStoreService auditLogService, Integer logQueueSize, Integer collectInterval,
                                  Integer queueFullWaitTime, AuditLogModelPool auditLogModelPool,
                                  Boolean batchEnabled, Integer batchSize, Long batchMaxWaitTime,
                                  LocalFileFallbackWriter fallbackWriter) {
        super(auditLogService, auditLogModelPool, fallbackWriter);

        this.collectInterval = collectInterval == null ? DEFAULT_LOG_COLLECT_INTERVAL : collectInterval;
        this.queueFullWaitTime = queueFullWaitTime == null ? 2000 : queueFullWaitTime;
        this.batchEnabled = batchEnabled != null && batchEnabled;
        this.batchSize = batchSize != null && batchSize > 0 ? batchSize : 100;
        this.batchMaxWaitTime = batchMaxWaitTime != null && batchMaxWaitTime > 0 ? batchMaxWaitTime : 1000L;

        synchronized (AuditLogAsyncCollector.class) {
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
            if (fallbackWriter != null) {
                fallbackWriter.write(auditLogModel);
                return;
            }
            log.warn("collectAuditLogModel async add logQueue error, logModel:{}", JSON.toJSONString(auditLogModel));
        }
    }

    @Override
    public void close() {
        started = false;

        // 等待收集线程处理完
        if (logCollectThread != null && logCollectThread.isAlive()) {
            try {
                logCollectThread.join(5000); // 最多等5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for collector thread");
            }
        }

        // Drain 队列中剩余数据
        drainQueue();

        if (auditLogModelPool != null) {
            auditLogModelPool.close();
        }

        logCollectThread = null;
        log.info("AsyncCollector closed gracefully");
    }

    private void drainQueue() {
        AuditLogModel remaining;
        int count = 0;
        while ((remaining = logQueue.poll()) != null) {
            try {
                auditLogService.saveAuditLog(remaining);
                count++;
            } catch (Exception e) {
                log.error("Drain failed, logId: {}", remaining.getId(), e);
            }
        }
        if (count > 0) {
            log.info("Drained {} remaining logs from queue", count);
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
                return;
            }
            if (auditLogModelPool != null) {
                auditLogModelPool.returnObject(auditLog);
            }
        }

        /**
         * 单条模式处理
         */
        private void handleSingleMode(AuditLogModel auditLog) {
            if (auditLog == null) {
                return;
            }
            fallbackIfFailed(auditLog, () -> {
                auditLogService.saveAuditLog(auditLog);
            });
            if (auditLogModelPool != null) {
                auditLogModelPool.returnObject(auditLog);
            }
        }

        /**
         * 批量保存
         */
        private void doBatchSave(List<AuditLogModel> logs) {
            fallbackBatchIfFailed(logs, () -> {
                auditLogService.batchSaveAuditLog(logs);
            });

            if (auditLogModelPool != null) {
                auditLogModelPool.returnObjects(logs);
            }
        }
    }
}
