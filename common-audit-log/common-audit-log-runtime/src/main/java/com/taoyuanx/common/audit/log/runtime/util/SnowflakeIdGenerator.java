package com.taoyuanx.common.audit.log.runtime.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.UUID;

/**
 * 雪花算法 ID 生成器
 * <p>
 * 基于 Twitter Snowflake 算法实现的分布式 ID 生成器
 * 支持自动计算 workerId 和 datacenterId，无需手动配置
 * </p>
 *
 * @author taoyuan
 * @date 2025/7/29
 */
@Slf4j
public class SnowflakeIdGenerator {

    /**
     * 起始时间戳 (2020-01-01 00:00:00)
     */
    private static final long START_TIMESTAMP = 1577808000000L;

    /**
     * 机器 ID 所占的位数
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 数据标识 ID 所占的位数
     */
    private static final long DATACENTER_ID_BITS = 5L;

    /**
     * 支持的最大机器 ID
     */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 支持的最大数据标识 ID
     */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /**
     * 序列在 ID 中所占的位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 机器 ID 向左移 12 位
     */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 数据标识 ID 向左移 17 位 (12 + 5)
     */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * 时间戳向左移 22 位 (5 + 5 + 12)
     */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /**
     * 生成序列的掩码
     */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /**
     * 工作机器 ID (0~31)
     */
    private final long workerId;

    /**
     * 数据中心 ID (0~31)
     */
    private final long datacenterId;

    /**
     * 毫秒内序列 (0~4095)
     */
    private long sequence = 0L;

    /**
     * 上次生成 ID 的时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 单例实例
     */
    private static volatile SnowflakeIdGenerator instance;

    /**
     * 获取单例实例（自动计算 workerId 和 datacenterId）
     */
    public static SnowflakeIdGenerator getInstance() {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    // 尝试从系统属性或环境变量中读取
                    Long configuredWorkerId = getSystemProperty("snowflake.worker.id", null);
                    Long configuredDatacenterId = getSystemProperty("snowflake.datacenter.id", null);
                    
                    long workerId;
                    long datacenterId;
                    
                    if (configuredWorkerId != null && configuredDatacenterId != null) {
                        // 用户已配置，使用配置值
                        workerId = configuredWorkerId;
                        datacenterId = configuredDatacenterId;
                        log.info("Using configured snowflake IDs: workerId={}, datacenterId={}", workerId, datacenterId);
                    } else {
                        // 自动生成（持久化文件 + 机器特征降级）
                        workerId = generateInstanceId("worker.id", () -> (long) (Math.abs(getHostInfo().hashCode()) % 32));
                        datacenterId = generateInstanceId("datacenter.id", () -> {
                            try {
                                return (long) (Math.abs(InetAddress.getLocalHost().getHostName().hashCode()) % 32);
                            } catch (Exception e) {
                                return (long) (Math.random() * 32);
                            }
                        });
                        log.info("Auto-generated snowflake IDs: workerId={}, datacenterId={}", workerId, datacenterId);
                    }
                    
                    instance = new SnowflakeIdGenerator(workerId, datacenterId);
                }
            }
        }
        return instance;
    }

    /**
     * 获取单例实例（自定义 workerId 和 datacenterId）
     */
    public static SnowflakeIdGenerator getInstance(long workerId, long datacenterId) {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    instance = new SnowflakeIdGenerator(workerId, datacenterId);
                }
            }
        }
        return instance;
    }

    /**
     * 重置单例实例（用于测试或重新配置）
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    /**
     * 通用实例ID生成方法
     * 
     * @param key 持久化键名
     * @param generator ID生成器（降级策略）
     * @return 生成的ID
     */
    private static long generateInstanceId(String key, java.util.function.Supplier<Long> generator) {
        // 方案1：尝试从持久化文件读取（推荐，容器环境友好）
        Long persistedId = loadPersistedId(key);
        if (persistedId != null) {
            return persistedId;
        }
        
        // 方案2：使用提供的生成器
        try {
            long id = generator.get();
            persistId(key, id);
            return id;
        } catch (Exception e) {
            log.warn("Failed to generate {} from generator", key, e);
        }
        
        // 兜底：随机生成并持久化
        long id = (long) (Math.random() * 32);
        persistId(key, id);
        log.warn("Using random {}: {}", key, id);
        return id;
    }

    /**
     * 从持久化文件加载 ID
     */
    private static Long loadPersistedId(String key) {
        try {
            String filePath = getInstanceIdFilePath();
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                return null;
            }
            
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                props.load(fis);
                String value = props.getProperty(key);
                if (value != null) {
                    return Long.parseLong(value);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to load persisted {}", key, e);
        }
        return null;
    }

    /**
     * 持久化 ID 到文件
     */
    private static synchronized void persistId(String key, long value) {
        try {
            String filePath = getInstanceIdFilePath();
            java.io.File file = new java.io.File(filePath);
            
            // 确保目录存在
            java.io.File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            java.util.Properties props = new java.util.Properties();
            
            // 如果文件已存在，先加载现有内容
            if (file.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    props.load(fis);
                }
            }
            
            // 更新或添加新值
            props.setProperty(key, String.valueOf(value));
            
            // 写入文件
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                props.store(fos, "Snowflake ID Generator - Auto-generated instance IDs");
            }
            
            log.info("Persisted {}={} to {}", key, value, filePath);
        } catch (Exception e) {
            log.warn("Failed to persist {}", key, e);
        }
    }

    /**
     * 获取实例ID文件路径
     * 默认：dataDir/snowflake-instance.properties
     */
    private static String getInstanceIdFilePath() {
        // 优先从系统属性读取
        String customPath = System.getProperty("snowflake.instance.file");
        if (customPath != null && !customPath.isEmpty()) {
            return customPath;
        }
        
        // 从 audit.log.dataDir 配置读取
        String dataDir = System.getProperty("audit.log.dataDir", "./data");
        return dataDir + "/snowflake-instance.properties";
    }

    /**
     * 获取主机信息（主机名 + IP）
     */
    private static String getHostInfo() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String ip = InetAddress.getLocalHost().getHostAddress();
            return hostname + "_" + ip;
        } catch (Exception e) {
            log.warn("Failed to get host info, using UUID", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 从系统属性或环境变量中获取配置
     */
    private static Long getSystemProperty(String key, Long defaultValue) {
        // 优先从系统属性读取
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // 忽略，使用默认值
            }
        }
        
        // 其次从环境变量读取
        String envKey = key.replace('.', '_').toUpperCase();
        value = System.getenv(envKey);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // 忽略，使用默认值
            }
        }
        
        return defaultValue;
    }

    /**
     * 构造函数
     *
     * @param workerId     工作机器 ID (0~31)
     * @param datacenterId 数据中心 ID (0~31)
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成下一个 ID
     *
     * @return ID
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 如果当前时间小于上一次 ID 生成的时间戳，说明系统时钟回退过
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                String.format("Clock moved backwards. Refusing to generate id for %d milliseconds",
                    lastTimestamp - timestamp));
        }

        // 如果是同一时间生成的，则进行毫秒内序列
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            // 毫秒内序列溢出
            if (sequence == 0) {
                // 阻塞到下一个毫秒，获得新的时间戳
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 时间戳改变，毫秒内序列重置
            sequence = 0L;
        }

        // 上次生成 ID 的时间戳
        lastTimestamp = timestamp;

        // 移位并通过或运算拼到一起组成 64 位的 ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
            | (datacenterId << DATACENTER_ID_SHIFT)
            | (workerId << WORKER_ID_SHIFT)
            | sequence;
    }

    /**
     * 阻塞到下一个毫秒，直到获得新的时间戳
     *
     * @param lastTimestamp 上次生成 ID 的时间戳
     * @return 当前时间戳
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 返回以毫秒为单位的当前时间
     *
     * @return 当前时间（毫秒）
     */
    protected long timeGen() {
        return System.currentTimeMillis();
    }
}
