package com.taoyuanx.common.audit.log.service;

import com.taoyuanx.common.audit.log.context.LogContext;

/**
 * @Author taoyuan @Date 2024/12/23 14:56 @Description 日志上下文填充处理器
 */
public interface AuditLogFillHandler {

    void fillAuditLog(LogContext logContext);
}
