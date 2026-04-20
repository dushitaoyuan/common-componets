package com.taoyuanx.common.audit.log.runtime.fallback;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件补偿处理器
 * 扫描待补偿文件，批量保存到数据库
 *
 * @author taoyuan
 * @date 2026-04-16
 */
@Slf4j
public class FileCompensationHandler {

    private final String directory;
    private final AuditLogStoreService storeService;
    private final CompensationIndexManager indexManager;
    private final LocalFileFallbackWriter fallbackWriter;
    private final AuditLogFallbackProperties.CompensationConfig compensationConfig;
    private final DeadLetterManager deadLetterManager;

    private volatile boolean running;
    private Thread compensationThread;
    private long scanInterval;
    
    // 熔断状态
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private int consecutiveFailures = 0;
    
    /**
     * 补偿结果统计（用于准确判断熔断）
     */
    private static class CompensationResult {
        int totalAttempts = 0;   // 尝试补偿的总条数
        int successCount = 0;    // 成功条数
        int failureCount = 0;    // 失败条数（包括写入死信的）
        
        void addSuccess(int count) {
            successCount += count;
            totalAttempts += count;
        }
        
        void addFailure(int count) {
            failureCount += count;
            totalAttempts += count;
        }
        
        boolean hasAttempts() {
            return totalAttempts > 0;
        }
        
        double getFailureRate() {
            if (totalAttempts == 0) return 0.0;
            return (double) failureCount / totalAttempts;
        }
    }
    
    // 当前周期的补偿结果（线程本地变量）
    private final ThreadLocal<CompensationResult> currentResult = new ThreadLocal<>();

    public FileCompensationHandler(String directory,
                                   AuditLogStoreService storeService,
                                   CompensationIndexManager indexManager,
                                   LocalFileFallbackWriter fallbackWriter,
                                   AuditLogFallbackProperties properties) {
        this.directory = directory;
        this.storeService = storeService;
        this.indexManager = indexManager;
        this.fallbackWriter = fallbackWriter;
        this.compensationConfig = properties.getCompensation();
        this.scanInterval = compensationConfig.getInitialInterval();
        
        // 初始化死信管理器（从 dataDir 推导）
        String dataDir = new File(directory).getParent();
        this.deadLetterManager = new DeadLetterManager(dataDir);
        
        // 初始化失败统计时间窗口
        if (compensationConfig.getFailureWindowDuration() != null) {
            indexManager.updateFailureWindowDuration(compensationConfig.getFailureWindowDuration());
        }
    }

    /**
     * 启动补偿线程
     */
    public void start() {
        running = true;
        compensationThread = new Thread(this::compensateLoop, "AuditLog-Compensation-Thread");
        compensationThread.setDaemon(true);
        compensationThread.start();
        log.info("Compensation handler started with initial interval: {}ms", scanInterval);
    }

    /**
     * 停止补偿线程
     */
    public void stop() {
        running = false;
        if (compensationThread != null) {
            compensationThread.interrupt();
            try {
                compensationThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭死信管理器
        if (deadLetterManager != null) {
            deadLetterManager.shutdown();
        }
        
        log.info("Compensation handler stopped");
    }

    /**
     * 补偿主循环
     */
    private void compensateLoop() {
        while (running) {
            try {
                // 检查熔断状态
                if (checkCircuitBreaker()) {
                    // 熔断开启中，等待恢复
                    continue;
                }
                
                // 初始化本次补偿结果跟踪
                currentResult.set(new CompensationResult());
                
                int processedCount = compensateAllFiles();
                
                // 获取本次补偿结果
                CompensationResult result = currentResult.get();
                currentResult.remove();
                
                // 根据实际补偿结果判断成功/失败
                // 如果有尝试补偿的记录，根据失败率判断；否则根据是否有待处理文件判断
                boolean compensationSuccess;
                if (result.hasAttempts()) {
                    // 有实际补偿尝试，根据成功率判断
                    compensationSuccess = result.successCount > 0;
                    log.debug("Compensation result: total={}, success={}, failure={}, rate={}%",
                            result.totalAttempts, result.successCount, result.failureCount,
                            String.format("%.2f", result.getFailureRate() * 100));
                } else {
                    // 无补偿尝试，检查是否有待处理文件
                    compensationSuccess = !hasPendingFiles();
                }
                indexManager.recordCompensationResult(compensationSuccess);

                // 动态调整扫描间隔
                if (processedCount == 0) {
                    // 无数据处理，指数退避（固定倍数2）
                    scanInterval = Math.min(scanInterval * 2,
                            compensationConfig.getMaxInterval());
                } else {
                    // 有数据处理，快速响应
                    scanInterval = compensationConfig.getInitialInterval();
                    if (compensationSuccess) {
                        consecutiveFailures = 0; // 成功后重置连续失败计数
                    }
                }
                
                // 检查失败率是否触发熔断
                checkFailureRate();

                Thread.sleep(scanInterval);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Compensation loop interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in compensation loop", e);
                // 记录失败
                indexManager.recordCompensationResult(false);
                consecutiveFailures++;
                
                // 发生异常后休眠一段时间再重试
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    /**
     * 检查熔断状态
     *
     * @return true 表示熔断开启中，应跳过补偿
     */
    private boolean checkCircuitBreaker() {
        if (!circuitBreakerOpen) {
            return false;
        }
        
        // 检查是否到达恢复时间
        long duration = compensationConfig.getCircuitBreakerDuration();
        if (System.currentTimeMillis() - circuitBreakerOpenTime >= duration) {
            // 尝试半开状态恢复
            circuitBreakerOpen = false;
            // 熔断恢复后使用更大的初始间隔
            scanInterval = compensationConfig.getInitialInterval() *
                    compensationConfig.getCircuitBreakerRecoveryMultiplier();
            log.info("Circuit breaker recovered, entering half-open state with interval: {}ms", scanInterval);
            return false;
        }
        
        // 熔断仍然开启，等待
        long remainingTime = duration - (System.currentTimeMillis() - circuitBreakerOpenTime);
        log.debug("Circuit breaker open, remaining time: {}ms", remainingTime);
        
        try {
            Thread.sleep(Math.min(remainingTime, 5000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }
    
    /**
     * 检查失败率是否触发熔断
     */
    private void checkFailureRate() {
        double failureRate = indexManager.getFailureRate();
        double threshold = compensationConfig.getFailureThreshold();
        
        // 窗口内至少有一定数据才判断（至少3次）
        int[] stats = indexManager.getWindowStats();
        int minSamples = 3;
        
        if (stats[0] >= minSamples && failureRate >= threshold) {
            // 触发熔断
            openCircuitBreaker(failureRate, stats);
        }
    }
    
    /**
     * 开启熔断
     */
    private void openCircuitBreaker(double failureRate, int[] stats) {
        circuitBreakerOpen = true;
        circuitBreakerOpenTime = System.currentTimeMillis();
        
        log.warn("Circuit breaker OPENED due to high failure rate: {}% (threshold: {}%), " +
                "window stats: {}/{} failures, will pause for {}ms",
                String.format("%.2f", failureRate * 100),
                String.format("%.2f", compensationConfig.getFailureThreshold() * 100),
                stats[1], stats[0],
                compensationConfig.getCircuitBreakerDuration());
        
        // 重置失败窗口，避免恢复后立即再次熔断
        indexManager.resetFailureWindow();
    }
    
    /**
     * 检查是否有待补偿的文件
     */
    private boolean hasPendingFiles() {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        return files != null && files.length > 0;
    }

    /**
     * 补偿所有文件
     *
     * @return 处理的文件数量
     */
    private int compensateAllFiles() {
        // 1. 扫描所有 .log 文件
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));

        if (files == null || files.length == 0) {
            return 0;
        }

        // 2. 按文件名排序（保证时间顺序）
        Arrays.sort(files);

        // 3. 逐个补偿
        int processedCount = 0;
        for (File file : files) {
            // 跳过空文件
            if (file.length() == 0) {
                continue;
            }

            if (compensateFile(file)) {
                processedCount++;
            }
        }

        return processedCount;
    }

    /**
     * 补偿单个文件
     *
     * @param file 文件
     * @return 是否处理了该文件
     */
    private boolean compensateFile(File file) {
        String fileName = file.getName();

        // 从索引读取已补偿行数
        int startLine = indexManager.getCompensatedLines(fileName);

        // 统计文件实际行数
        int actualLines = countLines(file);

        // 如果已补偿行数 >= 实际行数，跳过
        if (startLine >= actualLines) {
            return false;
        }

        // 如果是当前文件，需要加读锁
        boolean isCurrentFile = "audit_current.log".equals(fileName);
        if (isCurrentFile) {
            if (!fallbackWriter.tryLockForRead(compensationConfig.getReadLockTimeout())) {
                log.debug("Failed to acquire read lock for current file, skip");
                return false;
            }
        }

        try {
            // 执行补偿
            boolean success = doCompensate(file, fileName, startLine, actualLines, isCurrentFile);

            if (success && !isCurrentFile) {
                // 非当前文件且全部完成 → 删除文件和索引
                file.delete();
                indexManager.removeEntry(fileName);
                log.info("Compensated and deleted file: {}, lines={}", fileName, actualLines);
            }

            return success;

        } catch (Exception e) {
            log.error("Failed to compensate file: {}", fileName, e);
            return false;
        } finally {
            if (isCurrentFile) {
                fallbackWriter.unlockForRead();
            }
        }
    }

    /**
     * 执行补偿逻辑
     */
    private boolean doCompensate(File file, String fileName, int startLine,
                                 int actualLines, boolean isCurrentFile) {
        List<AuditLogModel> batch = new ArrayList<>();
        int currentLine = 0;
        int lastSuccessLine = startLine;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            // 跳过已补偿的行
            for (int i = 0; i < startLine; i++) {
                if (reader.readLine() == null) {
                    // 文件被截断，视为完成
                    return true;
                }
            }

            // 逐行读取并补偿
            String line;
            while ((line = reader.readLine()) != null) {
                currentLine++;
                int lineNumber = startLine + currentLine;
                
                // 检查是否超过最大重试次数
                if (indexManager.isPermanentlyFailed(fileName, lineNumber)) {
                    log.warn("Skip permanently failed line: {}, file: {}", lineNumber, fileName);
                    // 跳过该行，更新索引（不计入失败统计，因为是历史失败）
                    indexManager.updateCompensatedLines(fileName, lineNumber);
                    indexManager.clearFailure(fileName);
                    continue;
                }

                AuditLogModel model;
                try {
                    model = JSON.parseObject(line, AuditLogModel.class);
                } catch (Exception e) {
                    log.warn("Failed to parse line {} in file {}: {}", currentLine, fileName, e.getMessage());
                    // 解析失败，写入死信
                    writeParseDeadLetter(line, e, fileName, lineNumber);
                    // 跳过该行，记录失败
                    indexManager.updateCompensatedLines(fileName, lineNumber);
                    recordFailure(1);
                    continue;
                }
                
                batch.add(model);

                // 批量保存
                if (batch.size() >= compensationConfig.getBatchSize()) {
                    int savedCount = saveBatchWithRetry(batch, fileName, lineNumber - batch.size() + 1);
                    if (savedCount > 0) {
                        lastSuccessLine = lineNumber - batch.size() + savedCount;
                        indexManager.updateCompensatedLines(fileName, lastSuccessLine);
                        indexManager.clearFailure(fileName); // 成功后清除失败记录
                    }
                    batch.clear();
                }
            }

            // 处理剩余批次
            if (!batch.isEmpty()) {
                int savedCount = saveBatchWithRetry(batch, fileName, startLine + currentLine - batch.size() + 1);
                if (savedCount > 0) {
                    lastSuccessLine = startLine + currentLine - batch.size() + savedCount;
                    indexManager.updateCompensatedLines(fileName, lastSuccessLine);
                    indexManager.clearFailure(fileName); // 成功后清除失败记录
                }
            }

            // 判断是否全部完成
            if (isCurrentFile) {
                // 当前文件：只要补偿到最新行就算成功
                return true;
            } else {
                // 历史文件：必须补偿完所有行
                return lastSuccessLine >= actualLines;
            }

        } catch (IOException e) {
            log.error("Failed to read file: {}", fileName, e);
            // 失败时也要更新索引到最后一个成功位置
            if (lastSuccessLine > startLine) {
                indexManager.updateCompensatedLines(fileName, lastSuccessLine);
            }
            return false;
        }
    }

    /**
     * 保存一批数据（带重试和失败追踪）
     *
     * @param batch       批次数据
     * @param fileName    文件名
     * @param startLineNumber 起始行号
     * @return 成功保存的条数
     */
    private int saveBatchWithRetry(List<AuditLogModel> batch, String fileName, int startLineNumber) {
        try {
            storeService.batchSaveAuditLog(batch);
            // 批量成功，记录全部成功
            recordSuccess(batch.size());
            return batch.size();
            
        } catch (Exception e) {
            log.error("Failed to save batch, size: {}, will retry next scan", batch.size(), e);
            
            // 记录失败次数（整个批次作为一次失败）
            indexManager.recordFailure(fileName, startLineNumber);
            recordFailure(batch.size());
            
            // 返回0，整个批次下次重试
            return 0;
        }
    }
    
    /**
     * 记录成功（用于熔断判断）
     */
    private void recordSuccess(int count) {
        CompensationResult result = currentResult.get();
        if (result != null) {
            result.addSuccess(count);
        }
    }
    
    /**
     * 记录失败（用于熔断判断）
     */
    private void recordFailure(int count) {
        CompensationResult result = currentResult.get();
        if (result != null) {
            result.addFailure(count);
        }
    }
    
    /**
     * 写入解析失败的死信记录
     */
    private void writeParseDeadLetter(String rawLine, Throwable error, String fileName, int lineNumber) {
        try {
            deadLetterManager.writeDeadLetter(rawLine, error, fileName, lineNumber, 0);
        } catch (Exception e) {
            log.error("Failed to write parse dead letter", e);
        }
    }

    /**
     * 统计文件行数
     */
    private int countLines(File file) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
            log.warn("Failed to count lines in file: {}", file.getName(), e);
        }
        return count;
    }
}
