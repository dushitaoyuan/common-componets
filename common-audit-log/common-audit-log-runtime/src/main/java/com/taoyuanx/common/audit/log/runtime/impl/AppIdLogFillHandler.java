package com.taoyuanx.common.audit.log.runtime.impl;

import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.runtime.autoconfigure.AuditLogProperties;
import com.taoyuanx.common.audit.log.service.AuditLogFillHandler;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 应用标识填充
 * @author taoyuan
 * @date 2025/8/27 20:43
 */
@Component
public class AppIdLogFillHandler implements AuditLogFillHandler {
    @Autowired
    private AuditLogProperties auditLogProperties;

    @Override
    public void fillAuditLog(MethodInvocation methodInvocation) {
        AuditLogContextUtil.set(AuditLogContextUtil.CONTEXT_KEY_TENANT, auditLogProperties.getAppId());
    }
}
