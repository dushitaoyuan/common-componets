package com.taoyuanx.common.audit.log.aop;


import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogDiff;
import com.taoyuanx.common.audit.log.common.OperateLog;
import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.context.LogContext;
import com.taoyuanx.common.audit.log.diff.ObjectDiffHandler;
import com.taoyuanx.common.audit.log.diff.handler.NoDiffHandler;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.taoyuanx.common.audit.log.context.AuditLogContextUtil.*;

/**
 * @author taoyuan date 2024/12/23 16:22 description 日志记录拦截器
 */
@Slf4j
public class AuditLogMethodInterceptor implements MethodInterceptor, ApplicationContextAware, DisposableBean {


    private AuditLogCollector auditLogCollector;
    private List<AuditLogFillHandler> logFillHandlers;

    private AuditLogModelPool auditLogModelPool;
    private ApplicationContext applicationContext;


    public AuditLogMethodInterceptor(List<AuditLogFillHandler> logFillHandlers, AuditLogCollector auditLogCollector) {
        this.auditLogCollector = auditLogCollector;
        this.logFillHandlers = logFillHandlers;

    }

    public void setAuditLogModelPool(AuditLogModelPool auditLogModelPool) {
        this.auditLogModelPool = auditLogModelPool;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object result = null;
        Throwable ex = null;
        long startTime = System.currentTimeMillis();
        LogContext logContext = AuditLogUtil.initAuditLogContext(invocation, logFillHandlers);
        try {
            result = invocation.proceed();
        } catch (Throwable e) {
            ex = e;
            throw e;
        } finally {
            collectAuditLogModel(result, ex, logContext, startTime);
            AuditLogContextUtil.remove();
        }
        return result;
    }

    private void collectAuditLogModel(Object result, Throwable e, LogContext logContext, long startTime) {
        MethodInvocation methodInvocation = logContext.getMethodInvocation();
        try {
            Method method = methodInvocation.getMethod();
            OperateLog operateLog = findMergedOperateLog(method);
            if (operateLog == null) {
                return;
            }
            if (notNeedAuditLog(operateLog, e, methodInvocation, result)) {
                return;
            }
            long costTime = System.currentTimeMillis() - startTime;
            String logExp = operateLog.success();
            if (e != null && StringUtils.hasLength(operateLog.fail())) {
                logExp = operateLog.fail();
            }
            AuditLogModel operationLog = newAuditLogModel();
            operationLog.setBizType(operateLog.bizType());
            operationLog.setSubType(StringUtils.hasLength(operateLog.subBizType()) ? operateLog.subBizType() : null);
            operationLog.setOperateObject(SpElUtil.autoEval(methodInvocation, operateLog.operateObject(), result));
            operationLog.setOperateDesc(SpElUtil.autoEval(methodInvocation, logExp, result));
            fillAuditLog(operationLog, methodInvocation, result, e, logContext, costTime);
            auditLogCollector.collect(operationLog);
        } catch (Exception ex) {
            log.error("collectAuditLogModel error,methodInvocation:{},result:{}", methodInvocation, result, ex);
        }
    }

    private AuditLogModel newAuditLogModel() {
        return auditLogModelPool == null ? new AuditLogModel() : auditLogModelPool.borrowObject();
    }


    private boolean notNeedAuditLog(OperateLog logRecordAnno, Throwable e, MethodInvocation methodInvocation, Object result) {
        Class<? extends Throwable>[] ignoreExceptions = logRecordAnno.ignoreException();
        if (e != null && ignoreExceptions != null && ignoreExceptions.length > 0) {
            for (Class<? extends Throwable> exceptionType : ignoreExceptions) {
                if (exceptionType.isInstance(e)) {
                    return true;
                }
            }
        }
        String condition = logRecordAnno.condition();
        if (StringUtils.hasLength(condition)) {
            Boolean conditionResult = SpElUtil.eval(methodInvocation, condition, result, Boolean.class);
            return !(conditionResult != null && conditionResult);
        }
        return false;
    }

    private void fillAuditLog(AuditLogModel operationLog, MethodInvocation methodInvocation, Object result, Throwable e, LogContext logContext, Long costTime) {
        operationLog.setCostTime(costTime);
        long now = System.currentTimeMillis();
        operationLog.setOperateTime(now);
        operationLog.setOperator(logContext.get(CONTEXT_KEY_OPERATOR));
        if (e != null) {
            operationLog.setSuccess(false);
            operationLog.setErrorMsg(e.getMessage());
        } else {
            operationLog.setSuccess(true);
        }
        operationLog.setTenant(logContext.get(CONTEXT_KEY_TENANT));
        // 从时间戳计算 opDate
        operationLog.setOpDate(AuditLogUtil.formatTimestampToDateStr(now));
        operationLog.setExt(logContext.get(CONTEXT_KEY_EXT));
        String traceId = logContext.get(CONTEXT_KEY_TRACE_ID);
        if (StringUtils.hasLength(traceId)) {
            operationLog.setTraceId(traceId);
        }
        Object operateDsl = logContext.get(CONTEXT_KEY_OPERATE_DSL);
        if (operateDsl != null) {
            operationLog.setOperateDsl(operateDsl);
            return;
        }
        Object before = null;
        Object after = null;
        Method method = methodInvocation.getMethod();
        LogDiff logDiff = method.getAnnotation(LogDiff.class);
        if (logDiff != null) {
            before = getLogFieldValue(logDiff.before(), CONTEXT_KEY_BEFORE_OBJECT, logContext.getLogContextMap(), methodInvocation, result, Object.class);
            after = getLogFieldValue(logDiff.after(), CONTEXT_KEY_AFTER_OBJECT, logContext.getLogContextMap(), methodInvocation, result, Object.class);
            try {
                if (Objects.equals(logDiff.diffHandler(), NoDiffHandler.class)) {
                    operationLog.setOperateDsl(NoDiffHandler.NO_DIFF_HANDLER.diff(before, after));
                    return;
                }
                ObjectDiffHandler objectDiffHandler = getDiffHandlerFromSpring(logDiff.diffHandler());
                operationLog.setOperateDsl(objectDiffHandler.diff(before, after));
                return;
            } catch (Exception ex) {
                log.error("fillAuditLog logDiff error", e);
            }
        }

        if (before != null || after != null) {
            operationLog.setOperateDsl(NoDiffHandler.NO_DIFF_HANDLER.diff(before, after));
        }
        before = logContext.get(CONTEXT_KEY_BEFORE_OBJECT);
        after = logContext.get(CONTEXT_KEY_AFTER_OBJECT);
        if (before != null || after != null) {
            operationLog.setOperateDsl(NoDiffHandler.NO_DIFF_HANDLER.diff(before, after));
        }
    }


    private <T> T getLogFieldValue(String exp, String contextKey, Map<String, Object> logContextMap, MethodInvocation methodInvocation, Object result, Class<T> evalReturnType) {
        T value = null;
        if (StringUtils.hasLength(exp)) {
            value = SpElUtil.eval(methodInvocation, exp, result, evalReturnType);
        }
        if (value != null) {
            return value;
        }
        return AuditLogContextUtil.get(contextKey, logContextMap);
    }

    private ObjectDiffHandler getDiffHandlerFromSpring(Class<? extends ObjectDiffHandler> diffHandlerClass) {
        return applicationContext.getBean(ObjectDiffHandler.class);
    }


    /**
     * 获取@OperateLog注解，支持@SimpleOperateLog通过@AliasFor映射
     */
    private OperateLog findMergedOperateLog(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, OperateLog.class);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void destroy() throws Exception {
        auditLogCollector.close();

    }
}
