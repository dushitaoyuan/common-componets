package com.taoyuanx.common.audit.log.runtime.fallback;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 本地文件降级写入器测试
 *
 * @author taoyuan
 * @date 2026-04-18
 */
public class LocalFileFallbackWriterTest {

    private static final String TEST_DIR = "./data/test-fallback-writer";
    private LocalFileFallbackWriter writer;
    private CompensationIndexManager indexManager;

    @Before
    public void setUp() {
        cleanup();
        new File(TEST_DIR).mkdirs();
        
        indexManager = new CompensationIndexManager(TEST_DIR);
        
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.setDirectory(TEST_DIR);
        properties.setRotation(new AuditLogFallbackProperties.RotationConfig());
        properties.getRotation().setMaxLines(10); // 小阈值便于测试滚动
        properties.getRotation().setMaxSize(1024 * 1024L);
        properties.getRotation().setMaxAge(3600000L);
        
        writer = new LocalFileFallbackWriter(properties, indexManager);
    }

    @After
    public void tearDown() {
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                // ignore
            }
        }
        cleanup();
    }

    @Test
    public void testWriteSingleLog() throws Exception {
        AuditLogModel model = createTestLog(1L, "test_operator");
        writer.write(model);
        writer.close();

        File currentFile = new File(TEST_DIR, "audit_current.log");
        assertTrue(currentFile.exists());

        List<String> lines = readAllLines(currentFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("test_operator"));
    }

    @Test
    public void testWriteMultipleLogs() throws Exception {
        for (int i = 1; i <= 5; i++) {
            AuditLogModel model = createTestLog((long) i, "operator_" + i);
            writer.write(model);
        }
        writer.close();

        File currentFile = new File(TEST_DIR, "audit_current.log");
        List<String> lines = readAllLines(currentFile);
        assertEquals(5, lines.size());
    }

    @Test
    public void testFileRotationByLines() throws Exception {
        // 写入超过阈值的日志（触发滚动）
        for (int i = 1; i <= 15; i++) {
            AuditLogModel model = createTestLog((long) i, "operator_" + i);
            writer.write(model);
        }
        writer.close();

        // 验证当前文件已重置
        File currentFile = new File(TEST_DIR, "audit_current.log");
        List<String> currentLines = readAllLines(currentFile);
        assertTrue(currentLines.size() <= 10);

        // 验证有滚动后的文件
        File dir = new File(TEST_DIR);
        File[] rolledFiles = dir.listFiles((d, name) -> 
            name.startsWith("audit_") && name.endsWith(".log") && !name.equals("audit_current.log")
        );
        assertNotNull(rolledFiles);
        assertTrue(rolledFiles.length > 0);
    }

    @Test
    public void testConcurrentWrites() throws Exception {
        // 使用更大的阈值避免滚动
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.setDirectory(TEST_DIR);
        properties.setRotation(new AuditLogFallbackProperties.RotationConfig());
        properties.getRotation().setMaxLines(100); // 增大阈值
        properties.getRotation().setMaxSize(1024 * 1024L);
        properties.getRotation().setMaxAge(3600000L);
        
        writer.close();
        writer = new LocalFileFallbackWriter(properties, indexManager);
        
        Thread[] threads = new Thread[5];
        for (int t = 0; t < 5; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        AuditLogModel model = createTestLog(
                            (long) (threadId * 100 + i), 
                            "thread_" + threadId
                        );
                        writer.write(model);
                    }
                } catch (Exception e) {
                    fail("Concurrent write failed: " + e.getMessage());
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        writer.close();

        File currentFile = new File(TEST_DIR, "audit_current.log");
        List<String> lines = readAllLines(currentFile);
        assertEquals(15, lines.size()); // 5线程 * 3条 = 15条
    }

    @Test
    public void testReadLockForCompensation() throws Exception {
        // 使用更大的阈值避免滚动
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.setDirectory(TEST_DIR);
        properties.setRotation(new AuditLogFallbackProperties.RotationConfig());
        properties.getRotation().setMaxLines(100); // 增大阈值
        properties.getRotation().setMaxSize(1024 * 1024L);
        properties.getRotation().setMaxAge(3600000L);
        
        writer.close();
        indexManager = new CompensationIndexManager(TEST_DIR);
        writer = new LocalFileFallbackWriter(properties, indexManager);
        
        for (int i = 1; i <= 5; i++) {
            AuditLogModel model = createTestLog((long) i, "operator_" + i);
            writer.write(model);
        }
        
        // 关闭 writer 确保数据刷盘
        writer.close();
        
        // 重新创建 writer 以测试读锁
        indexManager = new CompensationIndexManager(TEST_DIR);
        writer = new LocalFileFallbackWriter(properties, indexManager);

        boolean locked = writer.tryLockForRead(100);
        assertTrue(locked);

        try {
            File currentFile = new File(TEST_DIR, "audit_current.log");
            List<String> lines = readAllLines(currentFile);
            assertEquals(5, lines.size());
        } finally {
            writer.unlockForRead();
        }
    }

    // 辅助方法：创建测试日志
    private AuditLogModel createTestLog(Long id, String operator) {
        AuditLogModel model = new AuditLogModel();
        model.setId(id);
        model.setOperator(operator);
        model.setBizType("TEST");
        model.setSubType("UNIT_TEST");
        model.setOperateDesc("Test operation");
        model.setOperateTime(System.currentTimeMillis());
        model.setSuccess(true);
        model.setTenant("test_tenant");
        return model;
    }

    // 辅助方法：读取所有行
    private List<String> readAllLines(File file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // 辅助方法：清理目录
    private void cleanup() {
        File dir = new File(TEST_DIR);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            dir.delete();
        }
    }
}
