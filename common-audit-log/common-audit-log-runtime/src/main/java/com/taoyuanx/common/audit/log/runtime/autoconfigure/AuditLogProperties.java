package com.taoyuanx.common.audit.log.runtime.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


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
     * 异步处理日志
     */
    private Boolean async = true;
    /**
     * 异步队列大小
     */
    private Integer logQueueSize = 1000;
    /**
     * 异步收集线程收集间隔
     */
    private Integer collectInterval = 50;

    /**
     * 收集队列满时的等待时间，默认休眠2s,负数则 入队失败丢弃
     */
    private Integer queueFullWaitTime = 2000;

    /**
     * 是否允许嵌套方法调用日志收集,默认支持
     */
    private Boolean allowNestLog = true;
    /**
     * 日志记录的package 范围
     */
    private String basePackages;
    /**
     * 是否使用对象池
     */
    private Boolean useObjectPool = true;
    /**
     * 对象池最大小
     */
    private Integer objectPoolMaxSize = 1024;
    /**
     * 对象池初始大小
     */
    private Integer  objectPoolInitSize= 100;

    /**
     * 是否使用disruptor队列
     */
    private Boolean useDisruptor = false;
    /**
     *  Disruptor环形缓冲区大小，必须是2的幂次方
     */
    private Integer ringBufferSize = 1024;

}
