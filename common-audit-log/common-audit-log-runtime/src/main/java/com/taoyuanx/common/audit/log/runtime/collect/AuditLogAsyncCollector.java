package com.taoyuanx.common.audit.log.runtime.collect;

import com.taoyuanx.common.audit.log.aop.AuditLogMethodInterceptor;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 异步内存队列搜集
 *
 * @author taoyuan
 * @date 2025/7/29 18:19
 */
@Slf4j
public class AuditLogAsyncCollector implements AuditLogCollector {
    private AuditLogService auditLogService;
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


    public AuditLogAsyncCollector(AuditLogService auditLogService, Integer logQueueSize, Integer collectInterval, Integer queueFullWaitTime, AuditLogModelPool auditLogModelPool) {
        this.auditLogService = auditLogService;
        this.collectInterval = collectInterval == null ? DEFAULT_LOG_COLLECT_INTERVAL : collectInterval;
        this.queueFullWaitTime = queueFullWaitTime == null ? 2000 : queueFullWaitTime;
        this.auditLogModelPool = auditLogModelPool;
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

    public AuditLogAsyncCollector(AuditLogService auditLogService, AuditLogModelPool auditLogModelPool) {
        this(auditLogService, DEFAULT_LOG_QUEUE_SIZE, DEFAULT_LOG_COLLECT_INTERVAL, -1, auditLogModelPool);
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        if (!started) {
            throw new IllegalStateException("AuditLogAsyncCollector 未启动");
        }
        if (queueFullWaitTime > 0) {
            if (logQueue.offer(auditLogModel, queueFullWaitTime, TimeUnit.MILLISECONDS)) {
                return;
            }
        } else {
            if (logQueue.offer(auditLogModel)) {
                return;
            }
        }
        log.warn("collect error,addLogQueue error,operationLog:{}", auditLogModel);
    }

    @Override
    public void close() {
        started = false;
        logCollectThread = null;
        if(auditLogModelPool!=null){
            auditLogModelPool.close();
        }

    }


    private class LogCollectThread extends Thread {

        public LogCollectThread() {
            super("AsyncLogCollectThread");
        }

        @Override
        public void run() {
            try {
                while (started) {
                    AuditLogModel auditLog = logQueue.poll(collectInterval, TimeUnit.MILLISECONDS);
                    doCollect(auditLog);
                    if (auditLogModelPool != null) {
                        auditLogModelPool.returnObject(auditLog);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while collecting log", e);
            } catch (Exception e) {
                log.error("Error while collecting log", e);
            }
        }
    }

    private void doCollect(AuditLogModel auditLog) {
        try {
            if (auditLog == null) {
                return;
            }
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("doCollect error slowCollectInfo:{}", auditLog, e);
        }
    }
}
