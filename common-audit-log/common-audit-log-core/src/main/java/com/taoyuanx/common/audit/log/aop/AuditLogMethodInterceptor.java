package com.taoyuanx.common.audit.log.aop;


import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogDiff;
import com.taoyuanx.common.audit.log.common.OperateLog;
import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.diff.ObjectDiffHandler;
import com.taoyuanx.common.audit.log.diff.handler.NoDiffHandler;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import static com.taoyuanx.common.audit.log.context.AuditLogContextUtil.*;

/**
 * @author taoyuan date 2024/12/23 16:22 description 日志记录拦截器
 */
@Slf4j
public class AuditLogMethodInterceptor implements MethodInterceptor, ApplicationContextAware {


    private AuditLogCollector auditLogCollector;
    private AuditLogFillHandler logFillHandler;

    private ApplicationContext applicationContext;

    public AuditLogMethodInterceptor(AuditLogFillHandler logFillHandler, AuditLogCollector auditLogCollector) {
        this.auditLogCollector = auditLogCollector;
        this.logFillHandler = logFillHandler;

    }


    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object result = null;
        Throwable ex = null;
        long startTime = System.currentTimeMillis();
        Map<String, Object> logContextMap = initAuditLogBefore(invocation);
        try {
            result = invocation.proceed();
        } catch (Throwable e) {
            ex = e;
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            collectAuditLogModel(invocation, result, ex, logContextMap, costTime);
            AuditLogContextUtil.remove();
        }
        return result;
    }

    private Map<String, Object> initAuditLogBefore(MethodInvocation methodInvocation) {
        // 初始化日志上下文
        Map<String, Object> logContextMap = AuditLogContextUtil.init();
        if (logFillHandler == null) {
            return logContextMap;
        }
        try {
            logFillHandler.fillAuditLog(methodInvocation);
        } catch (Throwable e) {
            log.warn("beforeAuditLog error,methodInvocation:{}", methodInvocation, e);
        }
        return logContextMap;
    }

    private void collectAuditLogModel(MethodInvocation methodInvocation, Object result, Throwable e, Map<String, Object> logContextMap, long costTime) {
        try {
            Method method = methodInvocation.getMethod();
            OperateLog operateLog = findMergedOperateLog(method);
            if (operateLog == null) {
                return;
            }
            if (notNeedAuditLog(operateLog, e, methodInvocation, result)) {
                return;
            }
            String logExp = operateLog.success();
            if (e != null && StringUtils.hasLength(operateLog.fail())) {
                logExp = operateLog.fail();
            }
            AuditLogModel operationLog = new AuditLogModel();
            operationLog.setBizType(operateLog.bizType());
            operationLog.setSubType(StringUtils.hasLength(operateLog.subBizType()) ? operateLog.subBizType() : null);
            operationLog.setOperateObject(SpElUtil.autoEval(methodInvocation, operateLog.operateObject(), result));
            operationLog.setOperateDesc(SpElUtil.autoEval(methodInvocation, logExp, result));
            fillAuditLog(operationLog, methodInvocation, result, e, logContextMap, costTime);
            auditLogCollector.collect(operationLog);
        } catch (Exception ex) {
            log.warn("saveAuditLog error,methodInvocation:{},result:{}", methodInvocation, result, ex);

        }
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

    private void fillAuditLog(AuditLogModel operationLog, MethodInvocation methodInvocation, Object result, Throwable e,
                              Map<String, Object> logContextMap,
                              Long costTime) {
        operationLog.setCostTime(costTime);
        operationLog.setOperateTime(new Date());
        operationLog.setOperator(AuditLogContextUtil.get(CONTEXT_KEY_OPERATOR, logContextMap));
        if (e != null) {
            operationLog.setSuccess(false);
            operationLog.setErrorMsg(e.getMessage());
        } else {
            operationLog.setSuccess(true);
        }
        operationLog.setTenant(AuditLogContextUtil.get(CONTEXT_KEY_TENANT, logContextMap));
        Date operateTime = operationLog.getOperateTime();
        if (operateTime != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            operationLog.setOpDate(sdf.format(operateTime));
        }
        operationLog.setExt(AuditLogContextUtil.get(CONTEXT_KEY_EXT, logContextMap));
        String traceId = AuditLogContextUtil.get(CONTEXT_KEY_TRACE_ID, logContextMap);
        if (StringUtils.hasLength(traceId)) {
            operationLog.setTraceId(traceId);
        }
        Object operateDsl = AuditLogContextUtil.get(CONTEXT_KEY_OPERATE_DSL, logContextMap);
        if (operateDsl != null) {
            operationLog.setOperateDsl(operateDsl);
            return;
        }
        Method method = methodInvocation.getMethod();
        LogDiff logDiff = method.getAnnotation(LogDiff.class);
        if (logDiff != null) {
            Object before = getLogFieldValue(logDiff.before(), CONTEXT_KEY_BEFORE_OBJECT, logContextMap, methodInvocation, result, Object.class);
            Object after = getLogFieldValue(logDiff.after(), CONTEXT_KEY_AFTER_OBJECT, logContextMap, methodInvocation, result, Object.class);
            try {
                if (Objects.equals(logDiff.diffHandler(), NoDiffHandler.class)) {
                    operationLog.setOperateDsl(NoDiffHandler.NO_DIFF_HANDLER.diff(before, after));
                    return;
                }
                ObjectDiffHandler objectDiffHandler = getDiffHandlerFromSpring(logDiff.diffHandler());
                operationLog.setOperateDsl(objectDiffHandler.diff(before, after));
            } catch (Exception ex) {
                log.error("fillAuditLog logDiff error", e);
                operationLog.setOperateDsl(NoDiffHandler.NO_DIFF_HANDLER.diff(before, after));
            }
            return;
        }
        Object before = AuditLogContextUtil.get(CONTEXT_KEY_BEFORE_OBJECT, logContextMap);
        Object after = AuditLogContextUtil.get(CONTEXT_KEY_AFTER_OBJECT, logContextMap);
        operationLog.setOperateDsl(NoDiffHandler.NO_DIFF_HANDLER.diff(before, after));
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
}
