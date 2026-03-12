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
     * 是否使用disruptor队列
     */
    private Boolean useDisruptor;

    /**
     * Disruptor环形缓冲区大小，必须是2的幂次方
     */
    private Integer ringBufferSize;

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
                break;

            case "normal":
                setNormal();
                break;

            case "high":
                // 高频场景：使用Disruptor收集器，较大的对象池和环形队列大小
                setDefaultValue("async", true);
                setDefaultValue("useDisruptor", true);
                setDefaultValue("ringBufferSize", 4096);
                setDefaultValue("useObjectPool", true);
                setDefaultValue("objectPoolMaxSize", 4096);
                setDefaultValue("objectPoolInitSize", 200);
                break;
            default:
                // 默认普通场景
                setNormal();
                break;
        }
        // 设置兜底默认值
        setDefaultValue("allowNestLog", true);
        setDefaultValue("logQueueSize", 1000);
        setDefaultValue("useObjectPool", true);
        setDefaultValue("objectPoolMaxSize", 512);
        setDefaultValue("objectPoolInitSize", 50);
        setDefaultValue("useDisruptor", false);
        setDefaultValue("ringBufferSize", 1024);

    }
    private void setNormal(){
        // 普通场景：使用jdk队列异步收集，较小的对象池
        setDefaultValue("async", true);
        setDefaultValue("logQueueSize", 1000);
        setDefaultValue("collectInterval", 50);
        setDefaultValue("useObjectPool", true);
        setDefaultValue("objectPoolMaxSize", 512);
        setDefaultValue("objectPoolInitSize", 50);
        setDefaultValue("useDisruptor", false);
    }

    /**
     * 设置默认值，仅当当前值为null时才设置
     * @param fieldName 字段名
     * @param value 默认值
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
