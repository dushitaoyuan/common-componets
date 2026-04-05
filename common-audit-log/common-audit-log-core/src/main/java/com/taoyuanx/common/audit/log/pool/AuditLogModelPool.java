package com.taoyuanx.common.audit.log.pool;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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
    
    // 清理任务间隔（毫秒），默认10秒
    private final long cleanupIntervalMs;

    public AuditLogModelPool() {
        this(1024, 100, 10000L); // 默认10秒清理一次
    }

    public AuditLogModelPool(int maxSize, int initialSize) {
        this(maxSize, initialSize, 10000L); // 默认10秒清理一次
    }
    
    public AuditLogModelPool(int maxSize, int initialSize, long cleanupIntervalMs) {
        this.maxSize = maxSize;
        this.initialSize = Math.min(initialSize, maxSize);
        this.pool = new ConcurrentLinkedQueue<>();
        this.cleanupIntervalMs = cleanupIntervalMs;
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
     * 注意：maxSize作为软限制，允许临时超出，由清理任务负责回收
     *
     * @param model AuditLogModel对象
     */
    public void returnObject(AuditLogModel model) {
        if (model == null || closed) {
            return;
        }

        AuditLogUtil.resetAuditLog(model);

        pool.offer(model);
        log.debug("Returned AuditLogModel to pool, current size: {}", getPoolSize());
    }
    
    /**
     * 批量归还AuditLogModel对象到池中
     * <p>相比逐个调用 returnObject()，批量归还可以减少日志输出次数</p>
     * <p>注意：maxSize作为软限制，允许临时超出，由清理任务负责回收</p>
     *
     * @param models AuditLogModel对象列表
     */
    public void returnObjects(List<AuditLogModel> models) {
        if (models == null || models.isEmpty() || closed) {
            return;
        }
        
        int returnedCount = 0;
        
        for (AuditLogModel model : models) {
            if (model == null) {
                continue;
            }
            
            AuditLogUtil.resetAuditLog(model);
            pool.offer(model);
            returnedCount++;
        }
        
        if (returnedCount > 0) {
            log.debug("Batch returned {} objects to pool, current size: {}", 
                     returnedCount, getPoolSize());
        }
    }


    /**
     * 启动定时检查任务
     * 根据配置的清理间隔执行，基于合理条件清理
     */
    private void startCleanupTask() {
        scheduler.scheduleWithFixedDelay(this::adaptiveCleanup, cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 自适应清理策略
     */
    private void adaptiveCleanup() {
        if (closed) {
            return;
        }

        int currentSize = getPoolSize();

        // 条件1：超过maxSize，立即清理到maxSize（防止内存无限增长）
        if (currentSize > maxSize) {
            cleanupToSize(maxSize);
            log.info("Exceeded maxSize({}), cleaned from {} to {}", maxSize, currentSize, maxSize);
            return;
        }

        // 条件2：长时间空闲且池大小超过初始大小，清理到初始大小
        long idleTime = System.currentTimeMillis() - lastBorrowTime;
        if (idleTime > IDLE_TIMEOUT && currentSize > initialSize) {
            int consecutive = consecutiveIdleChecks.incrementAndGet();
            if (consecutive >= IDLE_CHECK_THRESHOLD) {
                cleanupToSize(initialSize);
                consecutiveIdleChecks.set(0);
                log.info("Idle cleanup: reduced from {} to {} (idle for {}ms)",
                        currentSize, initialSize, idleTime);
                return;
            }
        } else {
            consecutiveIdleChecks.set(0);
        }

        // 条件3：池大小接近最大值且最近有活动，不清理
        if (currentSize > maxSize * 0.8 && idleTime < IDLE_TIMEOUT) {
            log.debug("Pool actively used (size: {}/{}), skipping cleanup", currentSize, maxSize);
            return;
        }

        // 条件4：池大小超过初始大小的2倍，但使用率不高，适度清理
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