package com.taoyuanx.common.audit.log.runtime.ext;

import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.context.LogContext;
import com.taoyuanx.common.audit.log.runtime.autoconfigure.AuditLogProperties;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 应用标识填充
 *
 * @author taoyuan
 * @date 2025/8/27 20:43
 */
@Component
public class AppIdLogFillHandler implements AuditLogFillHandler {
    @Autowired
    private AuditLogProperties auditLogProperties;


    @Override
    public void fillAuditLog(LogContext logContext) {
        if (logContext.get(AuditLogContextUtil.CONTEXT_KEY_TENANT) == null) {
            logContext.setLogContext(AuditLogContextUtil.CONTEXT_KEY_TENANT, auditLogProperties.getAppId());
        }
    }
}
