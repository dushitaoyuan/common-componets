package com.taoyuanx.common.audit.log.runtime.fallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * 补偿索引管理器测试
 *
 * @author taoyuan
 * @date 2026-04-18
 */
public class CompensationIndexManagerTest {

    private static final String TEST_DIR = "./data/test-index";
    private CompensationIndexManager indexManager;

    @Before
    public void setUp() {
        cleanup();
        File dir = new File(TEST_DIR);
        dir.mkdirs();
        indexManager = new CompensationIndexManager(TEST_DIR);
    }

    @After
    public void tearDown() {
        cleanup();
    }

    @Test
    public void testNewFileReturnsZero() {
        assertEquals(0, indexManager.getCompensatedLines("audit_test.log"));
    }

    @Test
    public void testUpdateAndPersist() throws Exception {
        indexManager.updateCompensatedLines("audit_test.log", 100);
        indexManager.flushIndex();

        // 模拟重启
        CompensationIndexManager newIndexManager = new CompensationIndexManager(TEST_DIR);
        assertEquals(100, newIndexManager.getCompensatedLines("audit_test.log"));
    }

    @Test
    public void testAtomicRename() throws Exception {
        indexManager.updateCompensatedLines("audit_current.log", 50);
        indexManager.flushIndex();
        
        indexManager.atomicRenameKey("audit_current.log", "audit_20260418_001.log");
        
        assertEquals(0, indexManager.getCompensatedLines("audit_current.log"));
        assertEquals(50, indexManager.getCompensatedLines("audit_20260418_001.log"));
    }

    @Test
    public void testRemoveEntry() throws Exception {
        indexManager.updateCompensatedLines("audit_test.log", 100);
        indexManager.flushIndex();
        
        indexManager.removeEntry("audit_test.log");
        
        assertEquals(0, indexManager.getCompensatedLines("audit_test.log"));
    }

    @Test
    public void testConcurrentUpdates() throws Exception {
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int value = i * 10;
            threads[i] = new Thread(() -> 
                indexManager.updateCompensatedLines("audit_concurrent.log", value)
            );
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        indexManager.flushIndex();
        int finalValue = indexManager.getCompensatedLines("audit_concurrent.log");
        assertTrue(finalValue >= 0 && finalValue <= 90);
    }

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
