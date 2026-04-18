package com.taoyuanx.common.audit.log.runtime.fallback;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 补偿索引管理器
 * 管理单一索引文件的读写、原子更新和延迟刷盘
 *
 * @author taoyuan
 * @date 2026-04-16
 */
@Slf4j
public class CompensationIndexManager {

    private final String indexPath;
    private final Properties index;
    private final ReentrantLock lock;
    private final Map<String, Integer> dirty;
    private volatile long lastFlushTime;
    private final long flushInterval;
    private static final long DEFAULT_FLUSH_INTERVAL = 5000; // 默认5秒
    
    // 当前失败追踪：fileName -> {lineNumber, retryCount}
    private final Map<String, int[]> currentFailures = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_COUNT = 3;

    public CompensationIndexManager(String directory) {
        this(directory, ".compensation_index", DEFAULT_FLUSH_INTERVAL);
    }

    public CompensationIndexManager(String directory, String indexFile, long flushInterval) {
        this.indexPath = directory + File.separator + indexFile;
        this.index = new Properties();
        this.lock = new ReentrantLock();
        this.dirty = new HashMap<>();
        this.lastFlushTime = System.currentTimeMillis();
        this.flushInterval = flushInterval;
        
        // 加载现有索引
        loadIndex();
    }

    /**
     * 加载索引文件
     */
    private void loadIndex() {
        File indexFile = new File(indexPath);
        if (indexFile.exists()) {
            try (FileInputStream fis = new FileInputStream(indexFile)) {
                index.load(fis);
                log.info("Loaded compensation index from {}", indexPath);
            } catch (IOException e) {
                log.error("Failed to load compensation index, will create new one", e);
            }
        } else {
            log.info("Compensation index file not found, will create new one");
        }
    }

    /**
     * 获取文件已补偿行数
     *
     * @param fileName 文件名
     * @return 已补偿行数，不存在则返回 0
     */
    public int getCompensatedLines(String fileName) {
        lock.lock();
        try {
            String value = index.getProperty(fileName);
            return value == null ? 0 : Integer.parseInt(value);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 更新已补偿行数（仅更新内存，延迟刷盘）
     *
     * @param fileName 文件名
     * @param lines    已补偿行数
     */
    public void updateCompensatedLines(String fileName, int lines) {
        lock.lock();
        try {
            index.setProperty(fileName, String.valueOf(lines));
            dirty.put(fileName, lines);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除索引条目
     *
     * @param fileName 文件名
     */
    public void removeEntry(String fileName) {
        lock.lock();
        try {
            index.remove(fileName);
            dirty.put(fileName, null); // 标记为需要删除
            flushIndex(); // 立即刷盘
        } finally {
            lock.unlock();
        }
    }

    /**
     * 原子重命名索引键（用于文件滚动时迁移索引）
     *
     * @param oldKey 旧键名
     * @param newKey 新键名
     */
    public void atomicRenameKey(String oldKey, String newKey) {
        lock.lock();
        try {
            String value = index.getProperty(oldKey);
            if (value != null) {
                index.setProperty(newKey, value);
                index.remove(oldKey);
                flushIndex(); // 立即刷盘
                log.info("Renamed index key: {} -> {}", oldKey, newKey);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检查是否需要刷盘
     */
    public void flushIfDirty() {
        if (dirty.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastFlushTime < flushInterval) {
            return;
        }

        flushIndex();
    }

    /**
     * 强制刷盘（原子写入）
     */
    public void flushIndex() {
        if (dirty.isEmpty()) {
            return;
        }

        lock.lock();
        try {
            doFlushIndex();
            dirty.clear();
            lastFlushTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 执行刷盘操作（临时文件 + 原子替换）
     */
    private void doFlushIndex() {
        File tempFile = new File(indexPath + ".tmp");
        File originalFile = new File(indexPath);

        try {
            // 1. 写入临时文件
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                index.store(fos, "Compensation Index");
                fos.getFD().sync(); // 强制刷盘
            }

            // 2. 原子替换
            Files.move(tempFile.toPath(), originalFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.debug("Flushed compensation index successfully");

        } catch (IOException e) {
            log.error("Failed to flush compensation index", e);
            // 删除临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 获取所有索引中的文件名
     *
     * @return 文件名集合
     */
    public java.util.Set<String> getAllFileNames() {
        lock.lock();
        try {
            return index.stringPropertyNames();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 记录当前行失败次数
     *
     * @param fileName   文件名
     * @param lineNumber 行号
     * @return 当前失败次数
     */
    public int recordFailure(String fileName, int lineNumber) {
        int[] failure = currentFailures.computeIfAbsent(fileName, k -> new int[]{lineNumber, 0});
        
        // 如果是新行，重置计数
        if (failure[0] != lineNumber) {
            failure[0] = lineNumber;
            failure[1] = 1;
        } else {
            failure[1]++;
        }
        
        log.warn("Recorded failure for file: {}, line: {}, retry count: {}", 
                fileName, lineNumber, failure[1]);
        return failure[1];
    }
    
    /**
     * 检查当前行是否超过最大重试次数
     *
     * @param fileName   文件名
     * @param lineNumber 行号
     * @return true 如果超过最大重试次数，应该跳过
     */
    public boolean isPermanentlyFailed(String fileName, int lineNumber) {
        int[] failure = currentFailures.get(fileName);
        if (failure == null) {
            return false;
        }
        // 必须是同一行且超过最大重试次数
        return failure[0] == lineNumber && failure[1] >= MAX_RETRY_COUNT;
    }
    
    /**
     * 清除失败记录（补偿成功后调用）
     *
     * @param fileName 文件名
     */
    public void clearFailure(String fileName) {
        currentFailures.remove(fileName);
    }
    
    /**
     * 获取最大重试次数
     */
    public int getMaxRetryCount() {
        return MAX_RETRY_COUNT;
    }
}
