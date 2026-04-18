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
                int processedCount = compensateAllFiles();

                // 动态调整扫描间隔
                if (processedCount == 0) {
                    // 无数据处理，指数退避（固定倍数2）
                    scanInterval = Math.min(scanInterval * 2,
                            compensationConfig.getMaxInterval());
                } else {
                    // 有数据处理，快速响应
                    scanInterval = compensationConfig.getInitialInterval();
                }

                Thread.sleep(scanInterval);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Compensation loop interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in compensation loop", e);
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
                    // 跳过该行，更新索引
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
                    // 跳过该行
                    indexManager.updateCompensatedLines(fileName, lineNumber);
                    continue;
                }
                
                batch.add(model);

                // 批量保存
                if (batch.size() >= compensationConfig.getBatchSize()) {
                    boolean success = saveBatchWithRetry(batch, fileName, lineNumber - batch.size() + 1);
                    if (success) {
                        lastSuccessLine = lineNumber;
                        indexManager.updateCompensatedLines(fileName, lastSuccessLine);
                        indexManager.clearFailure(fileName); // 成功后清除失败记录
                    }
                    batch.clear();
                }
            }

            // 处理剩余批次
            if (!batch.isEmpty()) {
                boolean success = saveBatchWithRetry(batch, fileName, startLine + currentLine - batch.size() + 1);
                if (success) {
                    lastSuccessLine = startLine + currentLine;
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
     * @return 是否成功
     */
    private boolean saveBatchWithRetry(List<AuditLogModel> batch, String fileName, int startLineNumber) {
        try {
            storeService.batchSaveAuditLog(batch);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to save batch, size: {}, will retry individually", batch.size(), e);
            
            // 批量失败，逐条重试
            for (int i = 0; i < batch.size(); i++) {
                AuditLogModel model = batch.get(i);
                int lineNumber = startLineNumber + i;
                
                try {
                    storeService.saveAuditLog(model);
                    
                } catch (Exception ex) {
                    // 记录失败次数
                    int retryCount = indexManager.recordFailure(fileName, lineNumber);
                    
                    // 检查是否超过最大重试次数
                    if (retryCount >= indexManager.getMaxRetryCount()) {
                        log.error("Line {} permanently failed after {} retries, writing to dead letter", 
                                 lineNumber, retryCount);
                        // 写入死信
                        deadLetterManager.writeDeadLetter(model, ex, fileName, lineNumber, retryCount);
                        // 跳过该行，更新索引
                        indexManager.updateCompensatedLines(fileName, lineNumber);
                        indexManager.clearFailure(fileName);
                    } else {
                        log.warn("Line {} failed, retry count: {}/{}, will retry next scan", 
                                lineNumber, retryCount, indexManager.getMaxRetryCount());
                        // 不更新索引，下次扫描会从这里重新开始
                        return false;
                    }
                }
            }
            
            return true;
        }
    }
    
    /**
     * 写入解析失败的死信记录
     */
    private void writeParseDeadLetter(String rawLine, Throwable error, String fileName, int lineNumber) {
        try {
            // 创建临时模型，只保留原始内容
            AuditLogModel tempModel = new AuditLogModel();
            // 不设置字段，让死信记录包含原始行内容即可
            
            deadLetterManager.writeDeadLetter(tempModel, error, fileName, lineNumber, 0);
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
