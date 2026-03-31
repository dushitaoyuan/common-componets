package com.taoyuanx.common.audit.log.runtime.autoconfigure;

import com.taoyuanx.common.audit.log.aop.AuditLogMethodInterceptor;
import com.taoyuanx.common.audit.log.aop.AuditLogPointcut;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogIdGenerator;
import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.diff.handler.FieldDiffHandler;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.runtime.collect.AuditLogAsyncCollector;
import com.taoyuanx.common.audit.log.runtime.collect.AuditLogDirectCollector;
import com.taoyuanx.common.audit.log.runtime.collect.AuditLogDisruptorCollector;
import com.taoyuanx.common.audit.log.runtime.impl.log.ShardingAuditLogService;
import com.taoyuanx.common.audit.log.runtime.impl.log.SingleTableAuditLogService;
import com.taoyuanx.common.audit.log.runtime.util.SnowflakeIdGenerator;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import com.taoyuanx.common.audit.log.service.AuditLogService;
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
        return new AuditLogProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogCollector auditLogCollector(@Autowired AuditLogService auditLogService, @Autowired AuditLogProperties auditLogProperties, @Autowired(required = false) AuditLogModelPool auditLogModelPool) {
        AuditLogContextUtil.initLocal(auditLogProperties.getAllowNestLog() == null || Objects.equals(auditLogProperties.getAllowNestLog(), true) ? true : false);
        if (Objects.equals(auditLogProperties.getAsync(), Boolean.TRUE)) {
            if (Objects.equals(auditLogProperties.getUseDisruptor(), Boolean.TRUE)) {
                return new AuditLogDisruptorCollector(auditLogService, auditLogProperties.getRingBufferSize(), auditLogModelPool);
            } else {
                return new AuditLogAsyncCollector(auditLogService, auditLogProperties.getLogQueueSize(), auditLogProperties.getCollectInterval(), auditLogProperties.getQueueFullWaitTime(), auditLogModelPool);
            }
        } else {
            return new AuditLogDirectCollector(auditLogService, auditLogModelPool);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("${audit.log.useObjectPool:false} or '${audit.log.logScene:normal}'=='normal'")
    public AuditLogModelPool auditLogModelPool(@Autowired AuditLogProperties auditLogProperties) {
        return new AuditLogModelPool(auditLogProperties.getObjectPoolMaxSize(), auditLogProperties.getObjectPoolInitSize());

    }


    @Bean
    @ConditionalOnMissingBean(AuditLogService.class)
    public AuditLogService auditLogService(JdbcTemplate jdbcTemplate, @Autowired AuditLogProperties auditLogProperties, @Autowired(required = false) LogIdGenerator logIdGenerator) {
        // 根据配置选择使用单表还是分表实现
        if (Boolean.TRUE.equals(auditLogProperties.getEnableSharding()) && auditLogProperties.getShardingTableCount() > 1) {
            return new ShardingAuditLogService(jdbcTemplate, auditLogProperties, logIdGenerator == null ? (LogIdGenerator) () -> SnowflakeIdGenerator.getInstance().nextId() : logIdGenerator);
        } else {
            return new SingleTableAuditLogService(jdbcTemplate, auditLogProperties, logIdGenerator);
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
    public FieldDiffHandler  fieldDiffHandler(){
        return new FieldDiffHandler();
    }
}
