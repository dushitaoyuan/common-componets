package com.taoyuanx.common.audit.log.runtime.autoconfigure;

import com.taoyuanx.common.audit.log.fallback.FallBackWriter;
import com.taoyuanx.common.audit.log.runtime.fallback.AuditLogFallbackProperties;
import com.taoyuanx.common.audit.log.runtime.fallback.LocalFileFallbackWriter;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

/**
 * 审计日志降级自动配置类
 * 只对外暴露 FallBackWriter 接口
 *
 * @author taoyuan
 * @date 2026-04-16
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AuditLogFallbackProperties.class)
@ConditionalOnProperty(prefix = "audit.log.fallback", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AuditLogStoreService.class)
public class AuditLogFallbackAutoConfiguration {

    @Autowired
    private AuditLogFallbackProperties properties;
    @Autowired
    private AuditLogProperties auditLogProperties;

    private LocalFileFallbackWriter writer;

    @Bean
    public FallBackWriter fallBackWriter(AuditLogStoreService storeService) {
        writer = new LocalFileFallbackWriter(auditLogProperties.getDataDir(),properties, storeService);
        log.info("Fallback system initialized");
        return writer;
    }

    @PreDestroy
    public void shutdown() {
        if (writer != null) writer.close();
        log.info("Fallback system shutdown");
    }
}
