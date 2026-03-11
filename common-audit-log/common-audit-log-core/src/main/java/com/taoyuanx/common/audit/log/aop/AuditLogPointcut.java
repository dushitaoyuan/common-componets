package com.taoyuanx.common.audit.log.aop;

import com.taoyuanx.common.audit.log.common.OperateLog;
import com.taoyuanx.common.audit.log.common.SimpleOperateLog;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * @author taoyuan
 */
public class AuditLogPointcut extends StaticMethodMatcherPointcut {

    private ClassFilter classFilter;

    public AuditLogPointcut(String basePackages) {
        if (StringUtils.hasLength(basePackages)) {
            this.classFilter = new PackageBasedClassFilter(basePackages);
        } else {
            this.classFilter = ClassFilter.TRUE;
        }
    }

    public AuditLogPointcut() {
        this.classFilter = ClassFilter.TRUE;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {

        return AnnotationUtils.findAnnotation(method, OperateLog.class) != null || AnnotationUtils.findAnnotation(method, SimpleOperateLog.class) != null;
    }

    @Override
    public ClassFilter getClassFilter() {
        return classFilter;
    }

    public class PackageBasedClassFilter implements ClassFilter {

        private String[] basePackages;

        public PackageBasedClassFilter(String basePackages) {
            this.basePackages = basePackages.split(",");
        }

        @Override
        public boolean matches(Class<?> clazz) {

            String className = clazz.getName();
            for (String basePackage : basePackages) {
                if (className.startsWith(basePackage)) {
                    return true;
                }
            }
            return false;
        }


    }
}
