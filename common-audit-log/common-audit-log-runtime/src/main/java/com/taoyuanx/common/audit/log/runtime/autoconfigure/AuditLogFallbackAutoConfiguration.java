package com.taoyuanx.common.audit.log.runtime.autoconfigure;

import com.taoyuanx.common.audit.log.runtime.fallback.*;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PreDestroy;

/**
 * 审计日志降级自动配置类
 *
 * @author taoyuan
 * @date 2026-04-16
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AuditLogFallbackProperties.class)
@ConditionalOnProperty(prefix = "audit.log.fallback", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AuditLogStoreService.class)
@EnableScheduling
public class AuditLogFallbackAutoConfiguration {

    @Autowired
    private AuditLogFallbackProperties fallbackProperties;

    private CompensationIndexManager indexManager;
    private LocalFileFallbackWriter fallbackWriter;
    private FileCompensationHandler compensationHandler;

    /**
     * 创建索引管理器
     */
    @Bean
    public CompensationIndexManager compensationIndexManager() {
        this.indexManager = new CompensationIndexManager(
                fallbackProperties.getDirectory()
        );
        log.info("Created CompensationIndexManager");
        return this.indexManager;
    }

    /**
     * 创建降级写入器
     */
    @Bean
    public LocalFileFallbackWriter localFileFallbackWriter(CompensationIndexManager indexManager) {
        this.fallbackWriter = new LocalFileFallbackWriter(fallbackProperties, indexManager);
        log.info("Created LocalFileFallbackWriter");
        return this.fallbackWriter;
    }

    /**
     * 创建补偿处理器并启动
     */
    @Bean
    public FileCompensationHandler fileCompensationHandler(AuditLogStoreService storeService,
                                                            CompensationIndexManager indexManager,
                                                            LocalFileFallbackWriter fallbackWriter) {
        this.compensationHandler = new FileCompensationHandler(
                fallbackProperties.getDirectory(),
                storeService,
                indexManager,
                fallbackWriter,
                fallbackProperties
        );
        
        // 启动补偿线程
        this.compensationHandler.start();
        log.info("Created and started FileCompensationHandler");
        
        return this.compensationHandler;
    }

    /**
     * 定时刷盘索引（延迟刷盘优化）
     */
    @Scheduled(fixedDelayString = "${audit.log.fallback.index.flush-interval:2000}")
    public void flushIndexPeriodically() {
        if (indexManager != null) {
            indexManager.flushIfDirty();
        }
    }

    /**
     * 优雅关闭
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down fallback components...");
        
        // 停止补偿线程
        if (compensationHandler != null) {
            compensationHandler.stop();
        }
        
        // 强制刷盘索引
        if (indexManager != null) {
            indexManager.flushIndex();
        }
        
        // 关闭写入器
        if (fallbackWriter != null) {
            fallbackWriter.close();
        }
        
        log.info("Fallback components shut down successfully");
    }
}
