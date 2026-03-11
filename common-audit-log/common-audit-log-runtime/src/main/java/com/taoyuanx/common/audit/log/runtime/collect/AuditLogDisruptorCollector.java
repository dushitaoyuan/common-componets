package com.taoyuanx.common.audit.log.runtime.collect;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEvent;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEventFactory;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEventHandler;
import com.taoyuanx.common.audit.log.service.AuditLogService;
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

    private static final int DEFAULT_RING_BUFFER_SIZE = 1024 * 10;
    private static final int DEFAULT_POOL_MAX_SIZE = 100;
    private static final int DEFAULT_POOL_INITIAL_SIZE = 10;

    public AuditLogDisruptorCollector(AuditLogService auditLogService, AuditLogModelPool auditLogModelPool) {
        this(auditLogService, DEFAULT_RING_BUFFER_SIZE, DEFAULT_POOL_MAX_SIZE, DEFAULT_POOL_INITIAL_SIZE, auditLogModelPool);
    }

    public AuditLogDisruptorCollector(AuditLogService auditLogService, Integer ringBufferSize, AuditLogModelPool auditLogModelPool) {
        this(auditLogService, ringBufferSize, DEFAULT_POOL_MAX_SIZE, DEFAULT_POOL_INITIAL_SIZE, auditLogModelPool);
    }

    public AuditLogDisruptorCollector(AuditLogService auditLogService, Integer ringBufferSize,
                                      Integer poolMaxSize, Integer poolInitialSize, AuditLogModelPool auditLogModelPool) {
        this.ringBufferSize = ringBufferSize == null ? DEFAULT_RING_BUFFER_SIZE : ringBufferSize;
        int maxPoolSize = poolMaxSize == null ? DEFAULT_POOL_MAX_SIZE : poolMaxSize;
        int initialPoolSize = poolInitialSize == null ? DEFAULT_POOL_INITIAL_SIZE : poolInitialSize;

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

        disruptor.handleEventsWith(new AuditLogEventHandler(auditLogService,auditLogModelPool));

        ringBuffer = disruptor.start();
        started = true;

        log.info("AuditLogDisruptorCollector started with ring buffer size: {}, pool max size: {}, pool initial size: {}",
                this.ringBufferSize, maxPoolSize, initialPoolSize);
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        if (!started) {
            throw new IllegalStateException("AuditLogDisruptorCollector 未启动");
        }
        try {
            long sequence = ringBuffer.next();
            try {
                AuditLogEvent event = ringBuffer.get(sequence);
                event.setAuditLog(auditLogModel);
            } finally {
                ringBuffer.publish(sequence);
            }
        } catch (Exception e) {
            log.error("Failed to publish audit log event: {}", auditLogModel, e);
            throw new RuntimeException("Failed to publish audit log event", e);
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
                }
                started = false;
            } catch (Exception e) {
                log.error("Error shutting down AuditLogDisruptorCollector", e);
            }
        }
    }


}