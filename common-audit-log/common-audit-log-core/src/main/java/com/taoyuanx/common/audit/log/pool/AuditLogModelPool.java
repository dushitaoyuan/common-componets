package com.taoyuanx.common.audit.log.pool;


import com.taoyuanx.common.audit.log.model.AuditLogModel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AuditLogModel对象池，用于复用AuditLogModel对象，减少GC压力
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogModelPool {
    private final ConcurrentLinkedQueue<AuditLogModel> pool;
    private final AtomicInteger size;
    private final int maxSize;
    private final int initialSize;

    public AuditLogModelPool() {
        this(1024, 100);
    }

    public AuditLogModelPool(int maxSize, int initialSize) {
        this.maxSize = maxSize;
        this.initialSize = Math.min(initialSize, maxSize);
        this.pool = new ConcurrentLinkedQueue<>();
        this.size = new AtomicInteger(0);

        // 初始化对象池
        for (int i = 0; i < this.initialSize; i++) {
            pool.offer(new AuditLogModel());
            size.incrementAndGet();
        }
    }

    /**
     * 从池中获取AuditLogModel对象
     * 如果池为空，则创建新对象
     *
     * @return AuditLogModel对象
     */
    public AuditLogModel borrowObject() {
        AuditLogModel model = pool.poll();
        if (model == null) {
            model = new AuditLogModel();
        } else {
            size.decrementAndGet();
        }
        return model;
    }


    /**
     * 将AuditLogModel对象归还到池中
     * 如果池已满，则丢弃对象
     *
     * @param model AuditLogModel对象
     */
    public void returnObject(AuditLogModel model) {
        if (model == null) {
            return;
        }

        clearModel(model);
        if (size.get() < maxSize) {
            pool.offer(model);
            size.incrementAndGet();
        }

    }

    /**
     * 清空AuditLogModel对象的状态
     *
     * @param model AuditLogModel对象
     */
    private void clearModel(AuditLogModel model) {
        model.setId(null);
        model.setTraceId(null);
        model.setOperator(null);
        model.setOperateObject(null);
        model.setBizType(null);
        model.setSubType(null);
        model.setOperateDesc(null);
        model.setOperateTime(null);
        model.setOperateDsl(null);
        model.setTenant(null);
        model.setSuccess(null);
        model.setCostTime(null);
        model.setErrorMsg(null);
        model.setOpDate(null);
        model.setExt(null);
    }

    /**
     * 获取当前池大小
     *
     * @return 当前池大小
     */
    public int getPoolSize() {
        return size.get();
    }

    /**
     * 获取池的最大容量
     *
     * @return 最大容量
     */
    public int getMaxSize() {
        return maxSize;
    }
}