package com.taoyuanx.common.audit.log.runtime.collect;

import com.alibaba.fastjson2.JSON;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogException;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEvent;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEventFactory;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEventHandler;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;

/**
 * 基于LMAX Disruptor的无锁队列异步日志收集器
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogDisruptorCollector implements AuditLogCollector {
    private final int ringBufferSize;
    private final Disruptor<AuditLogEvent> disruptor;
    private final RingBuffer<AuditLogEvent> ringBuffer;


    private volatile boolean started = false;

    private static final int DEFAULT_RING_BUFFER_SIZE = 1024;

    private AuditLogModelPool auditLogModelPool;

    public AuditLogDisruptorCollector(AuditLogStoreService auditLogService, Integer ringBufferSize,
                                      AuditLogModelPool auditLogModelPool,
                                      Boolean batchEnabled, Integer batchSize) {
        this.ringBufferSize = ringBufferSize == null ? DEFAULT_RING_BUFFER_SIZE : ringBufferSize;
        this.auditLogModelPool = auditLogModelPool;
        AuditLogEventFactory eventFactory = new AuditLogEventFactory();
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "AuditLog-Disruptor-Thread");
            thread.setDaemon(true);
            return thread;
        };
        disruptor = new Disruptor<>(
                eventFactory,
                this.ringBufferSize,
                threadFactory,
                ProducerType.MULTI,
                new com.lmax.disruptor.BlockingWaitStrategy()
        );
        
        boolean enableBatch = batchEnabled != null && batchEnabled;
        int size = batchSize != null && batchSize > 0 ? batchSize : 100;
        disruptor.handleEventsWith(new AuditLogEventHandler(auditLogService, auditLogModelPool, enableBatch, size));

        ringBuffer = disruptor.start();
        started = true;

        log.info("AuditLogDisruptorCollector started with ring buffer size: {}, batch enabled: {}",
                this.ringBufferSize, enableBatch);
    }
    
    public AuditLogDisruptorCollector(AuditLogStoreService auditLogService, Integer ringBufferSize,
                                      AuditLogModelPool auditLogModelPool) {
        this(auditLogService, ringBufferSize, auditLogModelPool, true, 100);
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        if (!started) {
            throw new IllegalStateException("AuditLogDisruptorCollector 未启动");
        }
        try {
            long sequence = ringBuffer.next();
            AuditLogEvent event = ringBuffer.get(sequence);
            event.setAuditLog(auditLogModel);
            ringBuffer.publish(sequence);
        } catch (Exception e) {
            log.error("disruptor collect log error,operationLog:{}", JSON.toJSONString(auditLogModel),e);
            throw new  LogException(e);
        }
    }

    /**
     * 关闭收集器
     */
    public void close() {
        if (started) {
            try {
                if (disruptor != null) {
                    disruptor.shutdown();
                    log.info("AuditLogDisruptorCollector shutdown successfully");
                    if (auditLogModelPool != null) {
                        auditLogModelPool.close();
                    }
                }
                started = false;
            } catch (Exception e) {
                log.error("Error shutting down AuditLogDisruptorCollector", e);
            }
        }
    }


}