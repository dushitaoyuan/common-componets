package com.taoyuanx.common.log.web.log;

import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;

/**
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/8/27 20:43
 */
@Component
public class LogFillHandler implements AuditLogFillHandler {
    @Override
    public void fillAuditLog(MethodInvocation methodInvocation) {
        // 填充操作人和操作租户,trace_id
        AuditLogContextUtil.set(AuditLogContextUtil.CONTEXT_KEY_TENANT, "default");
        AuditLogContextUtil.set(AuditLogContextUtil.CONTEXT_KEY_OPERATOR, "system");
    }
}
