package com.taoyuanx.common.audit.log.runtime.collect;

import com.alibaba.fastjson2.JSON;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogException;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.collect.AbstractAuditLogCollector;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEvent;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEventFactory;
import com.taoyuanx.common.audit.log.runtime.collect.lmax.AuditLogEventHandler;
import com.taoyuanx.common.audit.log.runtime.fallback.LocalFileFallbackWriter;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 基于LMAX Disruptor的无锁队列异步日志收集器
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogDisruptorCollector extends AbstractAuditLogCollector {
    private final int ringBufferSize;
    private final Disruptor<AuditLogEvent> disruptor;
    private final RingBuffer<AuditLogEvent> ringBuffer;


    private volatile boolean started = false;

    private static final int DEFAULT_RING_BUFFER_SIZE = 1024;

    public AuditLogDisruptorCollector(AuditLogStoreService auditLogService, Integer ringBufferSize, AuditLogModelPool auditLogModelPool, Boolean batchEnabled, Integer batchSize, LocalFileFallbackWriter fallbackWriter) {
        super(auditLogService, auditLogModelPool, fallbackWriter);

        this.ringBufferSize = ringBufferSize == null ? DEFAULT_RING_BUFFER_SIZE : ringBufferSize;
        AuditLogEventFactory eventFactory = new AuditLogEventFactory();
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "AuditLog-Disruptor-Thread");
            thread.setDaemon(true);
            return thread;
        };
        disruptor = new Disruptor<>(eventFactory, this.ringBufferSize, threadFactory, ProducerType.MULTI, new com.lmax.disruptor.BlockingWaitStrategy());

        boolean enableBatch = batchEnabled != null && batchEnabled;
        int size = batchSize != null && batchSize > 0 ? batchSize : 100;
        disruptor.handleEventsWith(new AuditLogEventHandler(this, enableBatch, size));

        ringBuffer = disruptor.start();
        started = true;

        log.info("AuditLogDisruptorCollector started with ring buffer size: {}, batch enabled: {}", this.ringBufferSize, enableBatch);
    }


    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        if (!started) {
            throw new IllegalStateException("AuditLogDisruptorCollector 未启动");
        }
        fallbackIfFailed(auditLogModel, () -> {
            long sequence = ringBuffer.next();
            AuditLogEvent event = ringBuffer.get(sequence);
            event.setAuditLog(auditLogModel);
            ringBuffer.publish(sequence);
        });
    }

    /**
     * 关闭收集器
     */
    public void close() {
        if (started && disruptor != null) {
            try {
                // 等待所有事件处理完成（最多10秒）
                disruptor.shutdown(10, TimeUnit.SECONDS);
                log.info("Disruptor shutdown successfully, all events processed");

                // Shutdown Hook 确保强制关闭
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    disruptor.halt();
                }));

                if (auditLogModelPool != null) {
                    auditLogModelPool.close();
                }
                started = false;
            } catch (Exception e) {
                log.error("Error during disruptor shutdown", e);
                disruptor.halt(); // 异常时强制关闭
            }
        }
    }


}