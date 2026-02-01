package com.taoyuanx.common.audit.log.aop;

import com.taoyuanx.common.audit.log.common.OperateLog;
import com.taoyuanx.common.audit.log.common.SimpleOperateLog;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;

/**
 * @author taoyuan
 */
public class AuditLogPointcut extends StaticMethodMatcherPointcut {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return AnnotationUtils.findAnnotation(method, OperateLog.class) != null || AnnotationUtils.findAnnotation(method, SimpleOperateLog.class) != null;
    }
}
