package com.taoyuanx.common.audit.log.runtime.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.Objects;

/**
 * 日志配置
 *
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/7/29 20:37
 */
@Data
@ConfigurationProperties(value = "audit.log")
public class AuditLogProperties {

    /**
     * 日志收集场景：低频(low)、普通(normal)、高频(high)
     */
    private String logScene = "normal";

    /**
     * 异步处理日志
     */
    private Boolean async;

    /**
     * 异步队列大小
     */
    private Integer logQueueSize;

    /**
     * 异步收集线程收集间隔
     */
    private Integer collectInterval;

    /**
     * 收集队列满时的等待时间，默认休眠2s,负数则 入队失败丢弃
     */
    private Integer queueFullWaitTime;

    /**
     * 是否允许嵌套方法调用日志收集,默认支持
     */
    private Boolean allowNestLog;

    /**
     * 日志记录的package 范围
     */
    private String basePackages;

    /**
     * 是否使用对象池
     */
    private Boolean useObjectPool;

    /**
     * 对象池最大大小
     */
    private Integer objectPoolMaxSize;

    /**
     * 对象池初始大小
     */
    private Integer objectPoolInitSize;

    /**
     * 对象池自适应清理间隔ms 建议值: 低频30000, 普通10000, 高频5000
     */
    private Long objectPoolCleanupIntervalMs;


    /**
     * 是否使用disruptor队列
     */
    private Boolean useDisruptor;

    /**
     * Disruptor环形缓冲区大小，必须是2的幂次方
     */
    private Integer ringBufferSize;

    /**
     * 是否启用分表
     */
    private Boolean enableSharding = false;

    /**
     * 分表数量
     */
    private Integer shardingTableCount = 1;

    /**
     * 日志表名称，默认为 op_log
     */
    private String logTableName = "op_log";

    /**
     * 日志明细表前缀，默认为 op_log_detail
     */
    private String logDetailTableName = "op_log_detail";

    /**
     * 应用标识
     */
    private String appId;
    /**
     * 是否启用日志详情表,默认不启用
     */
    private Boolean enableLogDetailTable;
    
    /**
     * 是否启用批量保存（仅异步模式有效）
     */
    private Boolean batchEnabled;
    
    /**
     * 批量保存大小
     */
    private Integer batchSize;
    
    /**
     * 批量保存最大等待时间(ms)
     */
    private Long batchMaxWaitTime;


    /**
     * 初始化方法，根据日志收集场景自动补全其他字段的值
     * 用户自定义配置优先于场景默认配置
     */
    @PostConstruct
    public void initByLogScene() {
        if (Objects.isNull(logScene)) {
            logScene = "normal";
        }

        switch (logScene.toLowerCase()) {
            case "low":
                // 低频场景：使用同步收集，不使用对象池
                setDefaultValue("async", false);
                setDefaultValue("useObjectPool", false);
                setDefaultValue("useDisruptor", false);
                setDefaultValue("batchEnabled", false);  // 不启用批量
                break;
            case "normal":
                setNormal();
                break;
            case "high":
                // 高频场景：Disruptor无锁队列 + 立即归还模式
                setDefaultValue("async", true);
                setDefaultValue("useDisruptor", true);
                setDefaultValue("ringBufferSize", 8192);         // 增大环形缓冲区,应对突发流量
                setDefaultValue("useObjectPool", true);
                setDefaultValue("objectPoolMaxSize", 4096);
                setDefaultValue("objectPoolInitSize", 200);
                setDefaultValue("batchEnabled", true);           // 启用批量
                setDefaultValue("batchSize", 100);               // 适中批量大小,平衡DB性能
                setDefaultValue("batchMaxWaitTime", 100L);       // 大幅缩短等待,减少对象占用时间
                setDefaultValue("objectPoolCleanupIntervalMs", 5000L);

                break;
            default:
                // 默认普通场景
                setNormal();
                break;
        }
        // 设置兜底默认值
        setDefaultValue("async", false);
        setDefaultValue("allowNestLog", true);


        // 异步队列默认配置
        setDefaultValue("logQueueSize", 1000);
        setDefaultValue("collectInterval", 50);
        setDefaultValue("queueFullWaitTime", 2000L);



        // 对象池默认配置
        setDefaultValue("useObjectPool", false);
        setDefaultValue("objectPoolMaxSize", 512);
        setDefaultValue("objectPoolInitSize", 50);
        setDefaultValue("objectPoolCleanupIntervalMs", 30000L);

        //  Disruptor默认配置
        setDefaultValue("useDisruptor", false);
        setDefaultValue("ringBufferSize", 1024);
        setDefaultValue("enableLogDetailTable", false);
        setDefaultValue("logTableName", "op_log");
        setDefaultValue("logDetailTableName", "op_log_detail");
        // 分表默认配置
        setDefaultValue("enableSharding", false);
        setDefaultValue("shardingTableCount", 1);
        
        // 批量配置兜底默认值
        setDefaultValue("batchEnabled", false);
        setDefaultValue("batchSize", 100);
        setDefaultValue("batchMaxWaitTime", 500L);




        if (appId == null) {
            throw new IllegalArgumentException("缺少配置:日志应用id");
        }

    }

    private void setNormal() {
        // 普通场景：异步收集 + 小批量优化
        setDefaultValue("async", true);
        setDefaultValue("logQueueSize", 1000);
        setDefaultValue("collectInterval", 50);
        setDefaultValue("useObjectPool", true);
        setDefaultValue("objectPoolMaxSize", 256);      // 降低池大小,提高复用率
        setDefaultValue("objectPoolInitSize", 50);
        setDefaultValue("useDisruptor", false);
        setDefaultValue("batchEnabled", true);           // 启用小批量
        setDefaultValue("batchSize", 30);                // 小批量平衡性能和对象占用
        setDefaultValue("batchMaxWaitTime", 200L);       // 较短等待时间
        setDefaultValue("objectPoolCleanupIntervalMs", 10000L);
    }

    /**
     * 设置默认值，仅当当前值为null时才设置
     *
     * @param fieldName 字段名
     * @param value     默认值
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
}
