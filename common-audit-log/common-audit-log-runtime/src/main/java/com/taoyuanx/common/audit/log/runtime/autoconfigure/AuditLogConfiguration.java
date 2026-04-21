package com.taoyuanx.common.audit.log.runtime.autoconfigure;

import com.taoyuanx.common.audit.log.aop.AuditLogMethodInterceptor;
import com.taoyuanx.common.audit.log.aop.AuditLogPointcut;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogIdGenerator;
import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.diff.handler.FieldDiffHandler;
import com.taoyuanx.common.audit.log.fallback.FallBackWriter;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.collect.AuditLogAsyncCollector;
import com.taoyuanx.common.audit.log.runtime.collect.AuditLogDirectCollector;
import com.taoyuanx.common.audit.log.runtime.collect.AuditLogDisruptorCollector;
import com.taoyuanx.common.audit.log.runtime.fallback.LocalFileFallbackWriter;
import com.taoyuanx.common.audit.log.runtime.impl.ShardingAuditLogService;
import com.taoyuanx.common.audit.log.runtime.impl.SingleTableAuditLogService;
import com.taoyuanx.common.audit.log.runtime.util.SnowflakeIdGenerator;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import com.taoyuanx.common.audit.log.service.AuditLogService;
import com.taoyuanx.common.audit.log.service.AuditLogStoreService;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;

/**
 * @author taoyuan date 2024/12/23 16:17 description 日志aop配置
 */
@Configuration
@ConditionalOnProperty(prefix = "audit.log", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AuditLogConfiguration {

    private static AuditLogPointcut DEFAULT_AUDIT_LOG_POINT_CUT = new AuditLogPointcut();


    @Bean
    public AbstractBeanFactoryPointcutAdvisor customAdvisor(@Autowired(required = false) AuditLogPointcut auditLogPointcut, @Autowired AuditLogCollector auditLogCollector, @Autowired(required = false) List<AuditLogFillHandler> logFillHandlers, @Autowired(required = false) AuditLogModelPool auditLogModelPool) {
        AuditLogPointcut realAuditLogPointcut = auditLogPointcut == null ? DEFAULT_AUDIT_LOG_POINT_CUT : auditLogPointcut;
        AuditLogBeanFactoryPointcutAdvisor advisor = new AuditLogBeanFactoryPointcutAdvisor(realAuditLogPointcut);
        AuditLogMethodInterceptor auditLogMethodInterceptor = new AuditLogMethodInterceptor(logFillHandlers, auditLogCollector);
        auditLogMethodInterceptor.setAuditLogModelPool(auditLogModelPool);
        advisor.setAdvice(auditLogMethodInterceptor);
        return advisor;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogPointcut auditLogPointcut(@Autowired AuditLogProperties auditLogProperties) {
        AuditLogPointcut auditLogPointcut = new AuditLogPointcut(auditLogProperties.getBasePackages());
        return auditLogPointcut;
    }

    @Bean
    public AuditLogProperties auditLogProperties() {
        AuditLogProperties properties = new AuditLogProperties();
        

        
        return properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogCollector auditLogCollector(@Autowired AuditLogStoreService auditLogStoreService, @Autowired AuditLogProperties auditLogProperties, @Autowired(required = false) AuditLogModelPool auditLogModelPool, @Autowired(required = false) FallBackWriter fallbackWriter) {
        AuditLogContextUtil.initLocal(auditLogProperties.getAllowNestLog() == null || Objects.equals(auditLogProperties.getAllowNestLog(), true) ? true : false);
        if (Objects.equals(auditLogProperties.getAsync(), Boolean.TRUE)) {
            if (Objects.equals(auditLogProperties.getUseDisruptor(), Boolean.TRUE)) {
                return new AuditLogDisruptorCollector(auditLogStoreService, auditLogProperties.getRingBufferSize(), auditLogModelPool,
                        auditLogProperties.getBatchEnabled(), auditLogProperties.getBatchSize(), fallbackWriter);
            } else {
                return new AuditLogAsyncCollector(auditLogStoreService, auditLogProperties.getLogQueueSize(),
                        auditLogProperties.getCollectInterval(), auditLogProperties.getQueueFullWaitTime(), auditLogModelPool,
                        auditLogProperties.getBatchEnabled(), auditLogProperties.getBatchSize(), auditLogProperties.getBatchMaxWaitTime(),
                        fallbackWriter);
            }
        } else {
            return new AuditLogDirectCollector(auditLogStoreService, auditLogModelPool, fallbackWriter);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("${audit.log.useObjectPool:false} or '${audit.log.logScene:normal}'=='normal' or '${audit.log.logScene:normal}'=='high'")
    public AuditLogModelPool auditLogModelPool(@Autowired AuditLogProperties auditLogProperties) {
        return new AuditLogModelPool(auditLogProperties.getObjectPoolMaxSize(), auditLogProperties.getObjectPoolInitSize(), auditLogProperties.getObjectPoolCleanupIntervalMs());

    }


    @Bean
    @ConditionalOnMissingBean(AuditLogStoreService.class)
    public AuditLogStoreService auditLogStoreService(JdbcTemplate jdbcTemplate, @Autowired AuditLogProperties auditLogProperties, @Autowired(required = false) LogIdGenerator logIdGenerator) {

        LogIdGenerator tempLogIdGenerator = logIdGenerator;
        if ((Objects.equals(auditLogProperties.getBatchEnabled(), Boolean.TRUE) && Objects.equals(auditLogProperties.getEnableLogDetailTable(), Boolean.TRUE)) || (Boolean.TRUE.equals(auditLogProperties.getEnableSharding()) && auditLogProperties.getShardingTableCount() > 1)) {
            // 开启批量且日志详情表，或分表时，需要有id生成器
            tempLogIdGenerator = logIdGenerator == null ? (LogIdGenerator) () -> SnowflakeIdGenerator.getInstance().nextId() : logIdGenerator;
        }
        // 根据配置选择使用单表还是分表实现
        if ( auditLogProperties.getShardingTableCount() > 1) {
            return new ShardingAuditLogService(jdbcTemplate, auditLogProperties, tempLogIdGenerator);
        } else {
            return new SingleTableAuditLogService(jdbcTemplate, auditLogProperties, tempLogIdGenerator);
        }
    }


    public static class AuditLogBeanFactoryPointcutAdvisor extends AbstractBeanFactoryPointcutAdvisor {
        private AuditLogPointcut auditLogPointcut;

        public AuditLogBeanFactoryPointcutAdvisor(AuditLogPointcut auditLogPointcut) {
            this.auditLogPointcut = auditLogPointcut;
        }

        @Override
        public Pointcut getPointcut() {
            return auditLogPointcut;
        }
    }

    @Bean
    public FieldDiffHandler fieldDiffHandler() {
        return new FieldDiffHandler();
    }


    @Bean
    public AuditLogService auditLogService(@Autowired AuditLogCollector auditLogCollector, @Autowired(required = false) List<AuditLogFillHandler> logFillHandlers) {
        return new AuditLogService(auditLogCollector, logFillHandlers);
    }
}
