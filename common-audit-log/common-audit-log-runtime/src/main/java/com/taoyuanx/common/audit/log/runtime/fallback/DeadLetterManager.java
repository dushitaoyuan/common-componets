package com.taoyuanx.common.audit.log.runtime.fallback;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 死信管理器
 * 将补偿失败的日志写入死信文件（JSON Lines格式），支持文件滚动和自动清理
 *
 * @author taoyuan
 * @date 2026-04-18
 */
@Slf4j
public class DeadLetterManager {
    
    private final String deadLetterDir;
    private final long maxFileSize; // 单文件最大大小（字节）
    private final long retentionDays; // 保留天数
    
    private volatile File currentFile;
    private volatile long currentFileSize;
    private final Object writeLock = new Object();
    
    private Thread cleanupThread;
    private volatile boolean running;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long DEFAULT_RETENTION_DAYS = 10;
    
    public DeadLetterManager(String dataDir) {
        this(dataDir, DEFAULT_MAX_FILE_SIZE, DEFAULT_RETENTION_DAYS);
    }
    
    public DeadLetterManager(String dataDir, long maxFileSize, long retentionDays) {
        this.deadLetterDir = dataDir + "/dead-letter";
        this.maxFileSize = maxFileSize;
        this.retentionDays = retentionDays;
        
        initDirectory();
        initCurrentFile();
        startCleanupTask();
    }
    
    /**
     * 初始化目录
     */
    private void initDirectory() {
        File dir = new File(deadLetterDir);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("Created dead letter directory: {}", deadLetterDir);
        }
    }
    
    /**
     * 初始化当前文件
     */
    private void initCurrentFile() {
        String today = DATE_FORMAT.format(new Date());
        currentFile = new File(deadLetterDir, "dead_" + today + ".log");
        
        if (currentFile.exists()) {
            currentFileSize = currentFile.length();
        } else {
            currentFileSize = 0;
        }
        
        log.info("Initialized dead letter file: {}, size: {} bytes", currentFile.getName(), currentFileSize);
    }
    
    /**
     * 写入死信记录（JSON Lines格式，追加写入）
     *
     * @param logModel   原始日志
     * @param error      失败异常
     * @param sourceFile 源文件名
     * @param lineNumber 行号
     * @param retryCount 重试次数
     */
    public void writeDeadLetter(AuditLogModel logModel, Throwable error, 
                                String sourceFile, int lineNumber, int retryCount) {
        synchronized (writeLock) {
            try {
                // 检查是否需要滚动
                if (currentFileSize >= maxFileSize) {
                    rotateFile();
                }
                
                // 构建记录
                DeadLetterRecord record = new DeadLetterRecord();
                record.setOriginalLog(logModel);
                record.setFailureReason(error.getClass().getSimpleName() + ": " + error.getMessage());
                record.setFailedAt(System.currentTimeMillis());
                record.setRetryCount(retryCount);

                // 序列化为 JSON
                String json = JSON.toJSONString(record);
                byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
                
                // 追加写入
                try (FileOutputStream fos = new FileOutputStream(currentFile, true)) {
                    fos.write(bytes);
                    fos.flush();
                }
                
                currentFileSize += bytes.length;
                
                log.error("Dead letter written to: {}, file: {}, line: {}, reason: {}", 
                         currentFile.getName(), sourceFile, lineNumber, record.getFailureReason());
                
            } catch (Exception e) {
                log.error("Failed to write dead letter", e);
            }
        }
    }
    
    /**
     * 文件滚动
     */
    private void rotateFile() {
        try {
            // 重命名当前文件为带时间戳
            String timestamp = TIMESTAMP_FORMAT.format(new Date());
            File rotatedFile = new File(deadLetterDir, "dead_" + timestamp + ".log");
            
            if (currentFile.renameTo(rotatedFile)) {
                log.info("Rotated dead letter file: {} -> {}", currentFile.getName(), rotatedFile.getName());
            } else {
                log.warn("Failed to rename file: {}", currentFile.getName());
                return;
            }
            
            // 创建新文件
            initCurrentFile();
            
        } catch (Exception e) {
            log.error("Failed to rotate dead letter file", e);
        }
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        running = true;
        cleanupThread = new Thread(() -> {
            while (running) {
                try {
                    cleanupExpiredFiles();
                    Thread.sleep(60 * 60 * 1000); // 每小时检查一次
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in cleanup task", e);
                }
            }
        }, "DeadLetter-Cleanup-Thread");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
        
        log.info("Dead letter cleanup task started, retention: {} days", retentionDays);
    }
    
    /**
     * 清理过期文件
     */
    private void cleanupExpiredFiles() {
        File dir = new File(deadLetterDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        
        if (files == null || files.length == 0) {
            return;
        }
        
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000);
        int deletedCount = 0;
        
        for (File file : files) {
            // 跳过当前正在写入的文件
            if (file.equals(currentFile)) {
                continue;
            }
            
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedCount++;
                    log.info("Deleted expired dead letter file: {}", file.getName());
                } else {
                    log.warn("Failed to delete expired file: {}", file.getName());
                }
            }
        }
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} expired dead letter files", deletedCount);
        }
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        running = false;
        if (cleanupThread != null) {
            cleanupThread.interrupt();
            try {
                cleanupThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Dead letter manager shutdown");
    }
    
    /**
     * 死信记录模型
     */
    @Data
    public static class DeadLetterRecord {
        /**
         * 原始日志
         */
        private AuditLogModel originalLog;
        
        /**
         * 失败原因
         */
        private String failureReason;
        
        /**
         * 失败时间
         */
        private Long failedAt;
        
        /**
         * 重试次数
         */
        private Integer retryCount;
    }
}
