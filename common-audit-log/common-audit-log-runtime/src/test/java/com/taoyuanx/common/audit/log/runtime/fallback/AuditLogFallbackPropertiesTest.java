package com.taoyuanx.common.audit.log.runtime.fallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 降级配置属性测试
 *
 * @author taoyuan
 * @date 2026-04-18
 */
public class AuditLogFallbackPropertiesTest {

    private String originalLogScene;

    @Before
    public void setUp() {
        // 保存原始值
        originalLogScene = System.getProperty("audit.log.logScene");
    }

    @After
    public void tearDown() {
        // 恢复原始值
        if (originalLogScene != null) {
            System.setProperty("audit.log.logScene", originalLogScene);
        } else {
            System.clearProperty("audit.log.logScene");
        }
    }

    @Test
    public void testLowFrequencyConfig() {
        System.setProperty("audit.log.logScene", "low");
        
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.initByLogScene();
        
        assertTrue(properties.getEnabled());
        assertEquals("./data/audit-fallback", properties.getDirectory());
        
        // 验证低频配置
        assertEquals(Integer.valueOf(5000), properties.getRotation().getMaxLines());
        assertEquals(Long.valueOf(50L * 1024 * 1024), properties.getRotation().getMaxSize());
        assertEquals(Long.valueOf(43200000L), properties.getRotation().getMaxAge());
        
        assertEquals(Long.valueOf(10000L), properties.getCompensation().getInitialInterval());
        assertEquals(Long.valueOf(120000L), properties.getCompensation().getMaxInterval());
        assertEquals(Integer.valueOf(50), properties.getCompensation().getBatchSize());
        assertEquals(Long.valueOf(5000L), properties.getCompensation().getReadLockTimeout());
    }

    @Test
    public void testNormalFrequencyConfig() {
        System.setProperty("audit.log.logScene", "normal");
        
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.initByLogScene();
        
        assertTrue(properties.getEnabled());
        assertEquals("./data/audit-fallback", properties.getDirectory());
        
        // 验证普通配置
        assertEquals(Integer.valueOf(10000), properties.getRotation().getMaxLines());
        assertEquals(Long.valueOf(100L * 1024 * 1024), properties.getRotation().getMaxSize());
        assertEquals(Long.valueOf(86400000L), properties.getRotation().getMaxAge());
        
        assertEquals(Long.valueOf(5000L), properties.getCompensation().getInitialInterval());
        assertEquals(Long.valueOf(60000L), properties.getCompensation().getMaxInterval());
        assertEquals(Integer.valueOf(100), properties.getCompensation().getBatchSize());
        assertEquals(Long.valueOf(3000L), properties.getCompensation().getReadLockTimeout());
    }

    @Test
    public void testHighFrequencyConfig() {
        System.setProperty("audit.log.logScene", "high");
        
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.initByLogScene();
        
        assertTrue(properties.getEnabled());
        assertEquals("./data/audit-fallback", properties.getDirectory());
        
        // 验证高频配置
        assertEquals(Integer.valueOf(50000), properties.getRotation().getMaxLines());
        assertEquals(Long.valueOf(500L * 1024 * 1024), properties.getRotation().getMaxSize());
        assertEquals(Long.valueOf(172800000L), properties.getRotation().getMaxAge());
        
        assertEquals(Long.valueOf(2000L), properties.getCompensation().getInitialInterval());
        assertEquals(Long.valueOf(30000L), properties.getCompensation().getMaxInterval());
        assertEquals(Integer.valueOf(200), properties.getCompensation().getBatchSize());
        assertEquals(Long.valueOf(2000L), properties.getCompensation().getReadLockTimeout());
    }

    @Test
    public void testDefaultConfigWhenNoScene() {
        System.clearProperty("audit.log.logScene");
        
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.initByLogScene();
        
        // 默认应该是 normal 配置
        assertEquals(Integer.valueOf(10000), properties.getRotation().getMaxLines());
        assertEquals(Long.valueOf(5000L), properties.getCompensation().getInitialInterval());
    }

    @Test
    public void testCustomConfigOverridesDefaults() {
        System.setProperty("audit.log.logScene", "normal");
        
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        // 先设置自定义值
        AuditLogFallbackProperties.RotationConfig customRotation = new AuditLogFallbackProperties.RotationConfig();
        customRotation.setMaxLines(99999);
        properties.setRotation(customRotation);
        
        properties.initByLogScene();
        
        // 自定义值应该保留
        assertEquals(Integer.valueOf(99999), properties.getRotation().getMaxLines());
        // 未设置的值应该使用默认值
        assertEquals(Long.valueOf(100L * 1024 * 1024), properties.getRotation().getMaxSize());
    }

    @Test
    public void testNullSafety() {
        System.setProperty("audit.log.logScene", "normal");
        
        AuditLogFallbackProperties properties = new AuditLogFallbackProperties();
        properties.initByLogScene();
        
        assertNotNull(properties.getRotation());
        assertNotNull(properties.getCompensation());
        assertNotNull(properties.getEnabled());
        assertNotNull(properties.getDirectory());
    }
}
