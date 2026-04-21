package com.taoyuanx.common.audit.log.context;

import org.aopalliance.intercept.MethodInvocation;

import java.util.HashMap;
import java.util.Map;

public class LogContext {
    private MethodInvocation methodInvocation;
    private Map<String, Object> logContextMap;


    public LogContext(MethodInvocation methodInvocation, Map<String, Object> logContextMap) {
        this.methodInvocation = methodInvocation;
        this.logContextMap = logContextMap;
    }

    public LogContext(Map<String, Object> logContextMap) {
        this.logContextMap = logContextMap;
    }
    public LogContext() {
        this(new HashMap<>());
    }

    public void setLogContext(String key, Object value) {
        if (methodInvocation != null) {
            AuditLogContextUtil.set(key, value);
        } else {
            if (value == null) {
                logContextMap.remove(key);
                return;
            }
            logContextMap.put(key, value);
        }
    }
    public Map<String, Object> getLogContextMap() {
        return logContextMap;
    }

    public MethodInvocation getMethodInvocation() {
        return methodInvocation;
    }

    public <T> T get(String key) {
        if (logContextMap != null) {
            return (T) logContextMap.get(key);
        }
        return  null;
    }
}
