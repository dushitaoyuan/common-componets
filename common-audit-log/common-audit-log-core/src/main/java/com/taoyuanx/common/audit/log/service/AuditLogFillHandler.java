package com.taoyuanx.common.audit.log.service;

import org.aopalliance.intercept.MethodInvocation;

/**
 * @Author taoyuan @Date 2024/12/23 14:56 @Description 日志上下文填充处理器
 */
public interface AuditLogFillHandler {

    void fillAuditLog(MethodInvocation methodInvocation);
}
