package com.taoyuanx.common.log.web.log;

import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.context.LogContext;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
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
    public void fillAuditLog(LogContext logContext) {
        logContext.setLogContext(AuditLogContextUtil.CONTEXT_KEY_OPERATOR, "system");

    }
}
