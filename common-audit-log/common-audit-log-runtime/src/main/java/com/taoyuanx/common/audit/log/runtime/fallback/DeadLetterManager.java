package com.taoyuanx.common.audit.log.runtime.fallback;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.util.FileUtil;
import com.taoyuanx.common.audit.log.runtime.util.LogUtil;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
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
    
    // 自适应清理参数
    private final int maxFileCount; // 最大文件数
    private final double diskUsageThreshold; // 磁盘使用率触发阈值（0-100）
    private final double diskUsageTarget; // 清理目标使用率
    private static final long DEFAULT_MAX_FILE_SIZE = 1000 * 1024 * 1024; // 1000MB
    private static final long DEFAULT_RETENTION_DAYS = 10;
    private static final int DEFAULT_MAX_FILE_COUNT = 100; // 默认最大100个文件
    private static final double DEFAULT_DISK_USAGE_THRESHOLD = 70.0; // 默认70%触发
    private static final double DEFAULT_DISK_USAGE_TARGET = 40.0; // 默认清理到40%





    private volatile File currentFile;
    private volatile long currentFileSize;
    private final Object writeLock = new Object();
    

    private static final String TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss";

    public DeadLetterManager(String dataDir) {
        this(dataDir, DEFAULT_MAX_FILE_SIZE, DEFAULT_RETENTION_DAYS, DEFAULT_MAX_FILE_COUNT);
    }
    
    public DeadLetterManager(String dataDir, long maxFileSize, long retentionDays) {
        this(dataDir, maxFileSize, retentionDays, DEFAULT_MAX_FILE_COUNT);
    }
    
    public DeadLetterManager(String dataDir, long maxFileSize, long retentionDays, int maxFileCount) {
        this(dataDir, maxFileSize, retentionDays, maxFileCount,
             DEFAULT_DISK_USAGE_THRESHOLD, DEFAULT_DISK_USAGE_TARGET);
    }
    
    /**
     * 完整构造函数
     */
    public DeadLetterManager(String dataDir, long maxFileSize, long retentionDays,
                            int maxFileCount, double diskUsageThreshold, double diskUsageTarget) {
        this.deadLetterDir = dataDir + "/dead-letter";
        this.maxFileSize = maxFileSize;
        this.retentionDays = retentionDays;
        this.maxFileCount = maxFileCount;
        this.diskUsageThreshold = diskUsageThreshold;
        this.diskUsageTarget = diskUsageTarget;
        FileUtil.foreMkDirs(deadLetterDir);
        initCurrentFile();
        log.info("DeadLetterManager initialized: retentionDays={}, maxFileCount={}, diskUsageThreshold={}%, diskUsageTarget={}%",
                retentionDays, maxFileCount, diskUsageThreshold, diskUsageTarget);
    }


    /**
     * 初始化当前文件
     */
    private void initCurrentFile() {
        String today = AuditLogUtil.formatTimestampToDateStr(System.currentTimeMillis());
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

        writeDeadLetter(LogUtil.logToString(logModel),error,sourceFile,lineNumber,retryCount);
    }
    public void writeDeadLetter(String originalLog, Throwable error,
                                String sourceFile, int lineNumber, int retryCount) {
        synchronized (writeLock) {
            try {
                // 检查是否需要滚动
                if (currentFileSize >= maxFileSize) {
                    rotateFile();
                }

                // 构建记录
                DeadLetterRecord record = new DeadLetterRecord();
                record.setOriginalLog(originalLog);
                record.setFailureReason(error.getClass().getSimpleName() + ": " + error.getMessage());
                record.setFailedAt(System.currentTimeMillis());
                record.setRetryCount(retryCount);

                // 序列化为 JSON
              //  String json = JSON.toJSONString(record);
                String json = JSON.toJSONString(originalLog);
                String line = json + "\n";

                // 追加写入（指定 UTF-8 编码）
                try (OutputStreamWriter osw = new OutputStreamWriter(
                        new FileOutputStream(currentFile, true), StandardCharsets.UTF_8)) {
                    osw.write(line);
                    osw.flush();
                }

                currentFileSize += line.getBytes(StandardCharsets.UTF_8).length;

                log.error("Dead letter written to: {}, file: {}, line: {}, reason: {}",
                        currentFile.getName(), sourceFile, lineNumber, record.getFailureReason());

            } catch (Exception e) {
                log.error("Failed to write dead letter", e);
            }
        }
    }
    
    /**
     * 自适应清理（三维度：时间、数量、磁盘使用率）
     */
    public void adaptiveCleanup() {
        try {
            File dir = new File(deadLetterDir);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
            
            if (files == null || files.length == 0) {
                return;
            }
            
            int totalDeleted = 0;
            
            // 维度1：清理过期文件（按时间）
            totalDeleted += cleanupByRetention(files);
            
            // 维度2：清理超量文件（按数量）
            totalDeleted += cleanupByCount(files);
            
            // 维度3：清理磁盘空间（按使用率）
            totalDeleted += cleanupByDiskUsage(files);
            
            if (totalDeleted > 0) {
                log.info("Adaptive cleanup completed: deleted {} files", totalDeleted);
            }
            
        } catch (Exception e) {
            log.error("Adaptive cleanup error", e);
        }
    }
    
    /**
     * 按保留天数清理
     */
    private int cleanupByRetention(File[] files) {
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000);
        int deleted = 0;
        
        for (File file : files) {
            if (file.equals(currentFile)) continue;
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deleted++;
                    log.info("Cleanup[retention]: deleted {}", file.getName());
                }
            }
        }
        return deleted;
    }
    
    /**
     * 按文件数量清理（保留最新的，删除最老的）
     */
    private int cleanupByCount(File[] files) {
        // 排除当前文件后的文件列表
        File[] eligibleFiles = Arrays.stream(files)
                .filter(f -> !f.equals(currentFile))
                .toArray(File[]::new);
        
        if (eligibleFiles.length <= maxFileCount) {
            return 0;
        }
        
        // 按修改时间排序，老的在前
        Arrays.sort(eligibleFiles, Comparator.comparingLong(File::lastModified));
        
        int deleteCount = eligibleFiles.length - maxFileCount;
        int deleted = 0;
        
        for (int i = 0; i < deleteCount; i++) {
            if (eligibleFiles[i].delete()) {
                deleted++;
                log.info("Cleanup[count]: deleted {}", eligibleFiles[i].getName());
            }
        }
        return deleted;
    }
    
    /**
     * 按磁盘使用率清理
     */
    private int cleanupByDiskUsage(File[] files) {
        double diskUsage = getDiskUsage();
        if (diskUsage < diskUsageThreshold) {
            return 0;
        }
        
        log.warn("Disk usage {}% exceeds threshold {}%, starting cleanup",
                diskUsage, diskUsageThreshold);
        
        // 按修改时间排序，老的在前
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        
        // 计算需要释放的空间：清理到目标使用率
        File disk = new File(deadLetterDir);
        long totalSpace = disk.getTotalSpace();
        long freeSpace = disk.getFreeSpace();
        long targetFreeSpace = (long) (totalSpace * (100 - diskUsageTarget) / 100);
        long needToFree = freeSpace - targetFreeSpace;
        
        if (needToFree <= 0) {
            needToFree = freeSpace / 2; // 至少释放一半空闲空间
        }
        
        int deleted = 0;
        long freed = 0;
        
        for (File file : files) {
            if (file.equals(currentFile)) continue;
            if (freed >= needToFree) break;
            
            long fileSize = file.length();
            if (file.delete()) {
                freed += fileSize;
                deleted++;
                log.info("Cleanup[disk]: deleted {}, freed {}", file.getName(), formatSize(fileSize));
            }
        }
        
        log.info("Cleanup[disk]: deleted {} files, freed {}", deleted, formatSize(freed));
        return deleted;
    }
    
    /**
     * 获取磁盘使用率百分比
     */
    private double getDiskUsage() {
        try {
            File disk = new File(deadLetterDir);
            long total = disk.getTotalSpace();
            long free = disk.getFreeSpace();
            if (total <= 0) return 0;
            return (1 - (double) free / total) * 100;
        } catch (Exception e) {
            log.warn("Failed to get disk usage", e);
            return 0;
        }
    }
    
    /**
     * 格式化文件大小
     */
    private String formatSize(long size) {
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format("%.2fKB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2fMB", size / (1024.0 * 1024));
        return String.format("%.2fGB", size / (1024.0 * 1024 * 1024));
    }

    /**
     * 文件滚动
     */
    private void rotateFile() {
        try {
            // 重命名当前文件为带时间戳
            String timestamp = LogUtil.fmtDate(new Date(),TIMESTAMP_FORMAT);
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
     * 死信记录模型
     */
    @Data
    public static class DeadLetterRecord {
        /**
         * 原始日志
         */
        private String originalLog;

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
