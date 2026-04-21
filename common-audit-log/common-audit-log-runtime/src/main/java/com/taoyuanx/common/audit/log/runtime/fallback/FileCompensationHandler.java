package com.taoyuanx.common.audit.log.runtime.fallback;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.util.LogUtil;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文件补偿处理器
 * 扫描待补偿文件，单条保存到数据库
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

    private ScheduledExecutorService scheduler;

    public FileCompensationHandler(String dataDir,
                                   AuditLogStoreService storeService,
                                   LocalFileFallbackWriter fallbackWriter,
                                   AuditLogFallbackProperties properties,CompensationIndexManager indexManager,long flushInterval) {

        this.directory = properties.getDirectory();
        this.storeService = storeService;
        this.fallbackWriter = fallbackWriter;
        this.compensationConfig = properties.getCompensation();
        this.scanInterval = compensationConfig.getInitialInterval();
        this.indexManager=indexManager;

        // 1. 死信管理器初始化
        this.deadLetterManager = new DeadLetterManager(dataDir);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Fallback-thread");
            t.setDaemon(true);
            return t;
        });
        // 2. 初始化索引
        if (compensationConfig.getFailureWindowDuration() != null) {
            indexManager.updateFailureWindowDuration(compensationConfig.getFailureWindowDuration());
        }
        // 每2s索引刷盘
        this.scheduler.scheduleWithFixedDelay(
                () -> indexManager.flushIfDirty(),
                5000, flushInterval, TimeUnit.MILLISECONDS
        );
        // 每5分钟检查并清理死信文件
        this.scheduler.scheduleWithFixedDelay(
                () -> deadLetterManager.adaptiveCleanup(),
                5000, 5 * 60 * 1000L, TimeUnit.MILLISECONDS
        );
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
        // 索引刷盘
        if (indexManager != null) {
            indexManager.flushIndex();
        }
        this.scheduler.shutdown();
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
                    Thread.sleep(compensationConfig.getInitialInterval());
                    continue;
                }
                
                int processedCount = compensateAllFiles();

                // 动态调整扫描间隔
                if (processedCount == 0) {
                    scanInterval = Math.min(scanInterval * 2,
                            compensationConfig.getMaxInterval());
                } else {
                    scanInterval = compensationConfig.getInitialInterval();
                }
                Thread.sleep(scanInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Compensation loop interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in compensation loop", e);
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
     * 检查并触发熔断（在具体补偿逻辑中调用）
     * @return true 表示触发了熔断
     */
    private boolean checkAndTriggerCircuitBreaker() {
        double failureRate = indexManager.getFailureRate();
        double threshold = compensationConfig.getFailureThreshold();
        
        int[] stats = indexManager.getWindowStats();
        int minSamples = 3;
        
        if (stats[0] >= minSamples && failureRate >= threshold) {
            openCircuitBreaker(failureRate, stats);
            return true;
        }
        return false;
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
     * 执行补偿逻辑（单条补偿模式）
     */
    private boolean doCompensate(File file, String fileName, int startLine,
                                 int actualLines, boolean isCurrentFile) {
        int currentLine = 0;
        int lastSuccessLine = startLine;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            // 跳过已补偿的行
            for (int i = 0; i < startLine; i++) {
                if (reader.readLine() == null) {
                    return true;
                }
            }

            String line;
            while ((line = reader.readLine()) != null) {
                currentLine++;
                int lineNumber = startLine + currentLine;
                
                if (indexManager.isPermanentlyFailed(fileName, lineNumber)) {
                    indexManager.updateCompensatedLines(fileName, lineNumber);
                    indexManager.clearFailure(fileName);
                    continue;
                }

                AuditLogModel model = parseLine(line, fileName, lineNumber);
                if (model == null) {
                    continue;
                }
                
                // 单条保存
                int savedCount = saveSingleWithRetry(model, fileName, lineNumber);
                if (savedCount > 0) {
                    lastSuccessLine = lineNumber;
                    indexManager.updateCompensatedLines(fileName, lastSuccessLine);
                    indexManager.clearFailure(fileName);
                    // 成功，记录到熔断统计
                    indexManager.recordCompensationResult(true);
                } else {
                    // 保存失败但已写入死信，更新索引跳过此行，避免重复写入死信
                    lastSuccessLine = lineNumber;
                    indexManager.updateCompensatedLines(fileName, lastSuccessLine);
                    // 失败，记录到熔断统计并检查是否触发熔断
                    indexManager.recordCompensationResult(false);
                    if (checkAndTriggerCircuitBreaker()) {
                        // 熔断触发，立即停止补偿，等待下次循环
                        log.warn("Circuit breaker triggered during compensation, stopping current file: {}", fileName);
                        return false;
                    }
                }
            }

            return isCurrentFile ? true : lastSuccessLine >= actualLines;

        } catch (IOException e) {
            log.error("Failed to read file: {}", fileName, e);
            if (lastSuccessLine > startLine) {
                indexManager.updateCompensatedLines(fileName, lastSuccessLine);
            }
            return false;
        }
    }
    
    /**
     * 解析行数据（使用 LogModelUtil 统一序列化/反序列化）
     * @return 解析成功返回model，失败返回null（解析失败已写入死信）
     */
    private AuditLogModel parseLine(String line, String fileName, int lineNumber) {
        try {
            return LogUtil.stringToLog(line);
        } catch (Exception e) {
            log.warn("Failed to parse lineNumber {} in file {}: {}", lineNumber, fileName, e.getMessage());
            writeParseDeadLetter(line, e, fileName, lineNumber);
            indexManager.updateCompensatedLines(fileName, lineNumber);
            // 记录解析失败到熔断统计
            indexManager.recordCompensationResult(false);
            return null;
        }
    }

    
    /**
     * 保存单条数据（带重试和失败追踪）
     *
     * @param model       单条数据
     * @param fileName    文件名
     * @param lineNumber  行号
     * @return 成功保存返回1，否则返回0
     */
    private int saveSingleWithRetry(AuditLogModel model, String fileName, int lineNumber) {
        try {
            storeService.saveAuditLog(model);
            return 1;
        } catch (Exception e) {
            log.error("Failed to save single record, file: {}, line: {}, will retry next scan",
                    fileName, lineNumber, e);
            
            // 记录失败次数
            indexManager.recordFailure(fileName, lineNumber);
            
            // 写入死信文件
            writeCompensationDeadLetter(model, e, fileName, lineNumber);
            
            return 0;
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
     * 写入补偿失败的死信记录
     */
    private void writeCompensationDeadLetter(AuditLogModel model, Throwable error,
                                              String fileName, int lineNumber) {
        try {
            deadLetterManager.writeDeadLetter(model, error, fileName, lineNumber, 0);
        } catch (Exception e) {
            log.error("Failed to write compensation dead letter", e);
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
