package com.taoyuanx.common.audit.log.runtime.fallback;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.Objects;

/**
 * 审计日志降级配置属性
 *
 * @author taoyuan
 * @date 2026-04-16
 */
@Data
@ConfigurationProperties(prefix = "audit.log.fallback")
public class AuditLogFallbackProperties {

    /**
     * 是否启用降级功能
     */
    private Boolean enabled;

    /**
     * 降级文件存储目录
     */
    private String directory;

    /**
     * 文件滚动策略配置
     */
    private RotationConfig rotation;

    /**
     * 补偿配置
     */
    private CompensationConfig compensation;

    /**
     * 初始化方法，根据日志收集场景自动补全默认值
     * 用户自定义配置优先于场景默认配置
     */
    @PostConstruct
    public void initByLogScene() {
        // 获取主配置的 logScene（通过系统属性或从外部传入）
        String logScene = System.getProperty("audit.log.logScene", "normal");
        
        // 获取 dataDir 配置
        String dataDir = System.getProperty("audit.log.dataDir", "./data");
        String fallbackDir = dataDir + "/fallback";
        
        switch (logScene.toLowerCase()) {
            case "low":
                // 低频场景：保守配置，减少资源占用
                setDefaultValue("enabled", true);
                setDefaultValue("directory", fallbackDir);
                
                if (rotation == null) rotation = new RotationConfig();
                rotation.initLow();
                
                if (compensation == null) compensation = new CompensationConfig();
                compensation.initLow();
                break;
                
            case "high":
                // 高频场景：激进配置，快速响应
                setDefaultValue("enabled", true);
                setDefaultValue("directory", fallbackDir);
                
                if (rotation == null) rotation = new RotationConfig();
                rotation.initHigh();
                
                if (compensation == null) compensation = new CompensationConfig();
                compensation.initHigh();
                break;
                
            case "normal":
            default:
                // 普通场景：平衡配置
                setDefaultValue("enabled", true);
                setDefaultValue("directory", fallbackDir);
                
                if (rotation == null) rotation = new RotationConfig();
                rotation.initNormal();
                
                if (compensation == null) compensation = new CompensationConfig();
                compensation.initNormal();
                break;
        }
    }

    /**
     * 设置默认值，仅当当前值为null时才设置
     */
    private void setDefaultValue(String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            if (field.get(this) == null) {
                field.set(this, value);
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }

    @Data
    public static class RotationConfig {
        /**
         * 最大行数阈值
         */
        private Integer maxLines;

        /**
         * 最大文件大小（字节）
         */
        private Long maxSize;

        /**
         * 最大存活时间（毫秒）
         */
        private Long maxAge;

        /**
         * 低频场景配置
         */
        public void initLow() {
            setDefault("maxLines", 5000);
            setDefault("maxSize", 50L * 1024 * 1024);  // 50MB
            setDefault("maxAge", 43200000L);  // 12小时
        }

        /**
         * 普通场景配置
         */
        public void initNormal() {
            setDefault("maxLines", 10000);
            setDefault("maxSize", 100L * 1024 * 1024);  // 100MB
            setDefault("maxAge", 86400000L);  // 24小时
        }

        /**
         * 高频场景配置
         */
        public void initHigh() {
            setDefault("maxLines", 50000);
            setDefault("maxSize", 500L * 1024 * 1024);  // 500MB
            setDefault("maxAge", 172800000L);  // 48小时
        }

        /**
         * 通用默认值设置方法
         */
        private void setDefault(String fieldName, Object value) {
            try {
                java.lang.reflect.Field field = this.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                if (field.get(this) == null) {
                    field.set(this, value);
                }
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    @Data
    public static class CompensationConfig {
        /**
         * 初始扫描间隔（毫秒）
         */
        private Long initialInterval;

        /**
         * 最大扫描间隔（毫秒）
         */
        private Long maxInterval;

        /**
         * 批量保存大小
         */
        private Integer batchSize;

        /**
         * 读取当前文件锁超时时间（毫秒）
         */
        private Long readLockTimeout;

        /**
         * 失败率统计时间窗口（毫秒），统计该时间范围内的补偿结果
         */
        private Long failureWindowDuration;

        /**
         * 失败率阈值（0.0-1.0），超过此阈值触发熔断
         */
        private Double failureThreshold;

        /**
         * 熔断持续时间（毫秒），熔断期间暂停补偿
         */
        private Long circuitBreakerDuration;

        /**
         * 熔断后恢复的初始间隔倍数
         */
        private Integer circuitBreakerRecoveryMultiplier;

        /**
         * 低频场景配置
         */
        public void initLow() {
            setDefault("initialInterval", 10000L);   // 10秒
            setDefault("maxInterval", 120000L);      // 2分钟
            setDefault("batchSize", 50);
            setDefault("readLockTimeout", 5000L);    // 5秒
            setDefault("failureWindowDuration", 60000L); // 1分钟时间窗口
            setDefault("failureThreshold", 0.6);     // 60%失败率触发熔断
            setDefault("circuitBreakerDuration", 60000L); // 熔断1分钟
            setDefault("circuitBreakerRecoveryMultiplier", 2);
        }

        /**
         * 普通场景配置
         */
        public void initNormal() {
            setDefault("initialInterval", 5000L);    // 5秒
            setDefault("maxInterval", 60000L);       // 60秒
            setDefault("batchSize", 100);
            setDefault("readLockTimeout", 3000L);    // 3秒
            setDefault("failureWindowDuration", 300000L); // 1分钟时间窗口
            setDefault("failureThreshold", 0.5);     // 50%失败率触发熔断
            setDefault("circuitBreakerDuration", 30000L); // 熔断30秒
            setDefault("circuitBreakerRecoveryMultiplier", 2);
        }

        /**
         * 高频场景配置
         */
        public void initHigh() {
            setDefault("initialInterval", 2000L);    // 2秒
            setDefault("maxInterval", 30000L);       // 30秒
            setDefault("batchSize", 200);
            setDefault("readLockTimeout", 2000L);    // 2秒
            setDefault("failureWindowDuration", 60000L); // 1分钟时间窗口
            setDefault("failureThreshold", 0.4);     // 40%失败率触发熔断
            setDefault("circuitBreakerDuration", 15000L); // 熔断15秒
            setDefault("circuitBreakerRecoveryMultiplier", 2);
        }

        /**
         * 通用默认值设置方法
         */
        private void setDefault(String fieldName, Object value) {
            try {
                java.lang.reflect.Field field = this.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                if (field.get(this) == null) {
                    field.set(this, value);
                }
            } catch (Exception e) {
                // 忽略
            }
        }
    }
}
