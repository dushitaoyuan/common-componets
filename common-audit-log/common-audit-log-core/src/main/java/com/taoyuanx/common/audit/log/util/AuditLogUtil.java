package com.taoyuanx.common.audit.log.util;

import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.context.LogContext;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;

import java.util.*;

@Slf4j
public class AuditLogUtil {
    /**
     * 轻量级克隆AuditLogModel
     * 由于对象字段都是基本类型或String引用,浅拷贝即可
     */
    public static AuditLogModel cloneAuditLog(AuditLogModel source) {
        if (source == null) {
            return null;
        }

        AuditLogModel target = new AuditLogModel();
        target.setId(source.getId());
        target.setTraceId(source.getTraceId());
        target.setOperator(source.getOperator());
        target.setOperateObject(source.getOperateObject());
        target.setBizType(source.getBizType());
        target.setSubType(source.getSubType());
        target.setOperateDesc(source.getOperateDesc());
        target.setOperateTime(source.getOperateTime());
        target.setOperateDsl(source.getOperateDsl());
        target.setTenant(source.getTenant());
        target.setSuccess(source.getSuccess());
        target.setCostTime(source.getCostTime());
        target.setErrorMsg(source.getErrorMsg());
        target.setOpDate(source.getOpDate());
        target.setExt(source.getExt());

        return target;
    }

    public static void resetAuditLog(AuditLogModel model) {
        if (model == null) {
            return;
        }
        model.setId(null);
        model.setTraceId(null);
        model.setOperator(null);
        model.setOperateObject(null);
        model.setBizType(null);
        model.setSubType(null);
        model.setOperateDesc(null);
        model.setOperateTime(null);
        model.setOperateDsl(null);
        model.setTenant(null);
        model.setSuccess(null);
        model.setCostTime(null);
        model.setErrorMsg(null);
        model.setOpDate(null);
        model.setExt(null);

    }

    public static LogContext initAuditLogContext(MethodInvocation methodInvocation, List<AuditLogFillHandler> logFillHandlers) {
        // 初始化日志上下文
        Map<String, Object> logContextMap = AuditLogContextUtil.init();
        LogContext logContext = new LogContext(methodInvocation, logContextMap);
        if (logFillHandlers == null || logFillHandlers.isEmpty()) {
            return logContext;
        }
        try {
            logFillHandlers.forEach(logFillHandler -> logFillHandler.fillAuditLog(logContext));
        } catch (Throwable e) {
            log.warn("initAuditLogContext error,logContext:{}", logContext, e);
        }
        return logContext;
    }

    /**
     * 合并日志上下文到AuditLogModel
     * <p>执行流程：</p>
     * <ol>
     *   <li>调用所有AuditLogFillHandler填充上下文</li>
     *   <li>从上下文中提取属性并覆盖到AuditLogModel</li>
     * </ol>
     *
     * @param logFillHandlers 日志填充处理器列表
     * @param auditLogModel   审计日志对象
     */
    public static void mergeLogContext(List<AuditLogFillHandler> logFillHandlers, AuditLogModel auditLogModel) {
        if (logFillHandlers == null || logFillHandlers.isEmpty()) {
            return;
        }

        try {
            LogContext logFillContext = new LogContext();
            // 2. 调用所有处理器填充上下文
            logFillHandlers.forEach(logFillHandler -> {
                try {
                    logFillHandler.fillAuditLog(logFillContext);
                } catch (Exception e) {
                    log.warn("fillAuditLog error, handler: {}", logFillHandler.getClass().getName(), e);
                }
            });

            // 3. 从上下文中提取属性并覆盖到AuditLogModel
            applyContextToLogModel(logFillContext, auditLogModel);

        } catch (Throwable e) {
            log.warn("mergeLogContext error, logFillHandlers size: {}", logFillHandlers.size(), e);
        }
    }

    /**
     * 将上下文中的属性应用到AuditLogModel
     *
     * @param logContext    上下文
     * @param auditLogModel 审计日志对象
     */
    private static void applyContextToLogModel(LogContext logContext, AuditLogModel auditLogModel) {
        Map<String, Object> contextMap = logContext.getLogContextMap();
        if (contextMap == null || contextMap.isEmpty() || auditLogModel == null) {
            return;
        }

        // 操作人
        String operator = logContext.get(AuditLogContextUtil.CONTEXT_KEY_OPERATOR);
        if (operator != null && auditLogModel.getOperator() == null) {
            auditLogModel.setOperator(operator);
        }

        // 租户
        String tenant = logContext.get(AuditLogContextUtil.CONTEXT_KEY_TENANT);
        if (tenant != null && auditLogModel.getTenant() == null) {
            auditLogModel.setTenant(tenant);
        }

        // Trace ID
        String traceId = logContext.get(AuditLogContextUtil.CONTEXT_KEY_TRACE_ID);
        if (traceId != null && auditLogModel.getTraceId() == null) {
            auditLogModel.setTraceId(traceId);
        }

        // 操作详情，操作时间等（operateDsl）
        auditLogModel.setCostTime(auditLogModel.getCostTime() == null ? 0L : auditLogModel.getCostTime());
        if (auditLogModel.getOperateTime() == null) {
            auditLogModel.setOperateTime(System.currentTimeMillis());
        }
        auditLogModel.setSuccess(auditLogModel.getErrorMsg() == null || auditLogModel.getErrorMsg().isEmpty());
        Long operateTime = auditLogModel.getOperateTime();
        if (operateTime != null) {
            auditLogModel.setOpDate(formatTimestampToDateStr(operateTime));
        }


    }




    /**
     * 将时间戳转换为日期字符串 yyyy-MM-dd
     */
    private static String formatTimestampToDateStr(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(new java.util.Date(timestamp));
    }


}
