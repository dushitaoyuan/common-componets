package com.taoyuanx.common.audit.log.pool;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AuditLogModel对象池，完善清理策略策略
 *
 * @author taoyuan
 * @date 2025/3/11
 */
@Slf4j
public class AuditLogModelPool {
    private final ConcurrentLinkedQueue<AuditLogModel> pool;
    private final int maxSize;
    private final int initialSize;
    private final ScheduledExecutorService scheduler;
    private volatile boolean closed = false;

    // 使用统计
    private final AtomicInteger consecutiveIdleChecks = new AtomicInteger(0);
    private volatile long lastBorrowTime = System.currentTimeMillis();
    private static final long IDLE_TIMEOUT = 300000; // 5分钟空闲超时
    private static final int IDLE_CHECK_THRESHOLD = 3; // 连续3次检查都空闲才清理

    public AuditLogModelPool() {
        this(1024, 100);
    }

    public AuditLogModelPool(int maxSize, int initialSize) {
        this.maxSize = maxSize;
        this.initialSize = Math.min(initialSize, maxSize);
        this.pool = new ConcurrentLinkedQueue<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "AuditLogModelPool-Cleanup"));

        // 初始化对象池
        initialize();
    }

    /**
     * 初始化对象池
     */
    private void initialize() {
        for (int i = 0; i < this.initialSize; i++) {
            pool.offer(new AuditLogModel());
        }

        // 启动定时检查任务，每60秒检查一次
        startCleanupTask();
    }

    /**
     * 从池中获取AuditLogModel对象
     * 如果池为空，则创建新对象
     *
     * @return AuditLogModel对象
     */
    public AuditLogModel borrowObject() {
        if (closed) {
            throw new IllegalStateException("Pool has been closed");
        }

        AuditLogModel model = pool.poll();
        if (model == null) {
            model = new AuditLogModel();
            log.debug("Created new AuditLogModel, pool size: {}", getPoolSize());
        } else {
            log.debug("Borrowed AuditLogModel from pool, remaining: {}", getPoolSize());
        }

        // 更新最后使用时间
        lastBorrowTime = System.currentTimeMillis();
        consecutiveIdleChecks.set(0); // 重置空闲检查计数

        return model;
    }

    /**
     * 将AuditLogModel对象归还到池中
     * 如果池已满，则丢弃对象
     *
     * @param model AuditLogModel对象
     */
    public void returnObject(AuditLogModel model) {
        if (model == null || closed) {
            return;
        }

        clearModel(model);

        // 检查池大小
        int currentSize = getPoolSize();
        if (currentSize < maxSize) {
            pool.offer(model);
            log.debug("Returned AuditLogModel to pool, current size: {}", getPoolSize());
        } else {
            log.debug("Pool full, discarding AuditLogModel, current size: {}", currentSize);
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
     * 启动定时检查任务
     * 每60秒检查一次，基于合理条件清理
     */
    private void startCleanupTask() {
        scheduler.scheduleWithFixedDelay(this::adaptiveCleanup, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 自适应清理策略
     */
    private void adaptiveCleanup() {
        if (closed) {
            return;
        }

        int currentSize = getPoolSize();

        // 条件1：长时间空闲且池大小超过初始大小
        long idleTime = System.currentTimeMillis() - lastBorrowTime;
        if (idleTime > IDLE_TIMEOUT && currentSize > initialSize) {
            int consecutive = consecutiveIdleChecks.incrementAndGet();
            if (consecutive >= IDLE_CHECK_THRESHOLD) {
                // 清理到初始大小
                cleanupToSize(initialSize);
                consecutiveIdleChecks.set(0);
                log.info("Idle cleanup: reduced from {} to {} (idle for {}ms)",
                        currentSize, initialSize, idleTime);
                return;
            }
        } else {
            consecutiveIdleChecks.set(0);
        }

        // 条件2：池大小接近最大值且最近有活动，不清理
        if (currentSize > maxSize * 0.8 && idleTime < IDLE_TIMEOUT) {
            log.debug("Pool actively used (size: {}/{}), skipping cleanup", currentSize, maxSize);
            return;
        }

        // 条件3：池大小超过初始大小的2倍，但使用率不高
        if (currentSize > initialSize * 2 && idleTime > IDLE_TIMEOUT / 2) {
            int targetSize = Math.min(currentSize / 2, initialSize * 3);
            cleanupToSize(targetSize);
            log.info("Size adjustment: reduced from {} to {} (moderate idle)", currentSize, targetSize);
        }
    }

    /**
     * 清理到指定大小
     * @param targetSize 目标大小
     */
    private void cleanupToSize(int targetSize) {
        int currentSize = getPoolSize();
        if (currentSize <= targetSize) {
            return;
        }

        int toRemove = currentSize - targetSize;
        int removed = 0;

        for (int i = 0; i < toRemove; i++) {
            AuditLogModel model = pool.poll();
            if (model != null) {
                removed++;
            } else {
                break;
            }
        }

        if (removed > 0) {
            log.debug("Cleaned up {} objects, pool size reduced from {} to {}",
                    removed, currentSize, getPoolSize());
        }
    }

    /**
     * 获取当前池大小
     * 直接使用队列的size()方法，ConcurrentLinkedQueue的size()是准确的
     *
     * @return 当前池大小
     */
    public int getPoolSize() {
        return pool.size();
    }

    /**
     * 获取池的最大容量
     *
     * @return 最大容量
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * 获取池的初始容量
     *
     * @return 初始容量
     */
    public int getInitialSize() {
        return initialSize;
    }

    /**
     * 获取空闲时间（毫秒）
     * @return 空闲时间
     */
    public long getIdleTime() {
        return System.currentTimeMillis() - lastBorrowTime;
    }

    /**
     * 获取连续空闲检查次数
     * @return 连续空闲检查次数
     */
    public int getConsecutiveIdleChecks() {
        return consecutiveIdleChecks.get();
    }

    /**
     * 关闭对象池
     */
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}