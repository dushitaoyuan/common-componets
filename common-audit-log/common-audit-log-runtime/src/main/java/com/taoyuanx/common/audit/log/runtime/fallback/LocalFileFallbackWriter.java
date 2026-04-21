package com.taoyuanx.common.audit.log.runtime.fallback;

import com.taoyuanx.common.audit.log.fallback.FallBackWriter;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.util.FileUtil;
import com.taoyuanx.common.audit.log.runtime.util.LogUtil;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 本地文件降级写入器
 * 将日志追加写入本地文件，管理文件滚动
 *
 * @author taoyuan
 * @date 2026-04-16
 */
@Slf4j
public class LocalFileFallbackWriter implements FallBackWriter{

    private final String directory;
    private final AuditLogFallbackProperties.RotationConfig rotationConfig;

    private File currentFile;
    private BufferedWriter writer;
    private int currentLines;
    private long fileCreateTime;
    private int sequence;

    private final ReentrantReadWriteLock rwLock;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;


    private volatile long lastFlushTime;
    private static final int FLUSH_LINE_THRESHOLD = 100;
    private static final long FLUSH_TIME_THRESHOLD = 5000; // 5秒

    private FileCompensationHandler compensationHandler;
    private  CompensationIndexManager indexManager;

    public LocalFileFallbackWriter(String dataDir,AuditLogFallbackProperties fallbackProperties, AuditLogStoreService storeService) {
        this.directory = fallbackProperties.getDirectory();
        this.rotationConfig = fallbackProperties.getRotation();
        this.rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        // 1. 初始化
        init();

        // 2. 启动补偿线程
        long flushInterval = 2000L;

        this.indexManager = new CompensationIndexManager(dataDir, ".compensation_index", flushInterval);
        this.compensationHandler = new FileCompensationHandler(dataDir,
                storeService,
                this,
                fallbackProperties,indexManager,flushInterval
        );
        this.compensationHandler.start();
        log.info("Fallback system initialized: writer, index, compensation, dead-letter");
        this.lastFlushTime = System.currentTimeMillis();
    }

    /**
     * 初始化当前文件
     */
    private void init() {
        FileUtil.foreMkDirs(directory);
        currentFile = new File(directory, "audit_current.log");
        try {
            if (!currentFile.exists()) {
                currentFile.createNewFile();
                fileCreateTime = System.currentTimeMillis();
            } else {
                long fileTime = currentFile.lastModified();
                fileCreateTime = fileTime != 0 ? fileTime : System.currentTimeMillis();
            }
            writer = new BufferedWriter(new FileWriter(currentFile, true));
            currentLines = countLines(currentFile);
            sequence = findMaxSequence();

            log.info("Initialized fallback writer: {}, lines={}", currentFile.getPath(), currentLines);

        } catch (IOException e) {
            log.error("Failed to initialize fallback writer", e);
            throw new RuntimeException("Failed to initialize fallback writer", e);
        }
    }

    /**
     * 写入一条日志
     *
     * @param model 审计日志模型
     */
    public void write(AuditLogModel model) {
        writeLock.lock();
        try {
            // 检查是否需要滚动
            if (shouldRotate()) {
                rotateFile();
            }

            String json = LogUtil.logToString(model);
            writer.write(json);
            writer.newLine();
            currentLines++;

            // 自动刷盘
            autoFlush();
            log.debug("fallback saved file,logContent:{}", LogUtil.logToString(model));
        } catch (Throwable e) {
            log.error("Failed to write audit log to fallback file,logContent:{}", LogUtil.logToString(model), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void write(List<AuditLogModel> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        writeLock.lock();
        try {
            for (AuditLogModel model : logs) {
                // 检查是否需要滚动
                if (shouldRotate()) {
                    rotateFile();
                }
                String json = LogUtil.logToString(model);
                writer.write(json);
                writer.newLine();
                currentLines++;
            }
            autoFlush();
            log.debug("Batch wrote {} logs to fallback file,logContent:{}", logs.size(), LogUtil.logToString(logs));
        } catch (Throwable e) {
            log.error("Failed to batch write audit logs to fallback file, size: {},logContent:{}", logs.size(), LogUtil.logToString(logs),e);
        } finally {
            writeLock.unlock();
        }
    }



    /**
     * 自动刷盘
     */
    private void autoFlush() throws IOException {
        // 行数和时间
        boolean byLines = currentLines % FLUSH_LINE_THRESHOLD == 0;
        boolean byTime = System.currentTimeMillis() - lastFlushTime > FLUSH_TIME_THRESHOLD;
        
        if (byLines || byTime) {
            writer.flush();
            lastFlushTime = System.currentTimeMillis();
        }
    }

    /**
     * 检查是否需要滚动
     */
    private boolean shouldRotate() {
        long fileSize = currentFile.length();
        long fileAge = System.currentTimeMillis() - fileCreateTime;

        return currentLines >= rotationConfig.getMaxLines()
                || fileSize >= rotationConfig.getMaxSize()
                || fileAge >= rotationConfig.getMaxAge();
    }

    /**
     * 文件滚动
     */
    private void rotateFile() {
        try {
            // 1. 关闭前强制刷盘
            writer.flush();
            writer.close();

            // 2. 生成新文件名
            String newFileName = generateFileName();
            File newFile = new File(directory, newFileName);

            // 3. 重命名文件
            Files.move(currentFile.toPath(), newFile.toPath());
            log.info("Rotated file: audit_current.log -> {}", newFileName);

            // 4. 迁移索引
            indexManager.atomicRenameKey("audit_current.log", newFileName);

            // 5. 创建新文件
            currentFile = new File(directory, "audit_current.log");
            writer = new BufferedWriter(new FileWriter(currentFile, true));
            currentLines = 0;
            fileCreateTime = System.currentTimeMillis();

            // 6. 初始化新文件索引
            indexManager.updateCompensatedLines("audit_current.log", 0);
            indexManager.flushIndex(); // 立即刷盘
            
            // 重置刷盘时间
            lastFlushTime = System.currentTimeMillis();

            log.info("Created new current file, reset lines to 0");

        } catch (IOException e) {
            log.error("Failed to rotate file", e);
            throw new RuntimeException("Failed to rotate file", e);
        }
    }

    /**
     * 生成新文件名
     */
    private String generateFileName() {
        sequence++;
        String dateStr = AuditLogUtil.formatTimestampToDateStr(System.currentTimeMillis());
        return String.format("audit_%s_%03d.log", dateStr, sequence);
    }

    /**
     * 尝试获取读锁（用于补偿线程读取当前文件）
     *
     * @param timeout 超时时间（毫秒）
     * @return 是否成功获取锁
     */
    public boolean tryLockForRead(long timeout) {
        try {
            return readLock.tryLock(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放读锁
     */
    public void unlockForRead() {
        readLock.unlock();
    }

    /**
     * 关闭写入器
     */
    public void close() {
        writeLock.lock();
        try {
            if (writer != null) {
                writer.close();
                log.info("Closed fallback writer");
            }
            compensationHandler.stop();
        } catch (IOException e) {
            log.error("Failed to close fallback writer", e);
        } finally {
            writeLock.unlock();
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

    /**
     * 查找最大序列号
     */
    private int findMaxSequence() {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.startsWith("audit_") && name.endsWith(".log"));

        if (files == null || files.length == 0) {
            return 0;
        }

        int maxSeq = 0;
        for (File file : files) {
            String name = file.getName();
            // 从文件名提取序列号：audit_20260416_003.log → 3
            int lastUnderscore = name.lastIndexOf('_');
            int dotIndex = name.lastIndexOf('.');
            if (lastUnderscore > 0 && dotIndex > lastUnderscore) {
                try {
                    String seqStr = name.substring(lastUnderscore + 1, dotIndex);
                    int seq = Integer.parseInt(seqStr);
                    maxSeq = Math.max(maxSeq, seq);
                } catch (NumberFormatException e) {
                    // 忽略解析失败的文件名
                }
            }
        }

        return maxSeq;
    }
}
