package com.taoyuanx.common.audit.log.common;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @author taoyuan date 2024/12/23 14:08 description 日志注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@OperateLog(
    bizType = "",
    subBizType = "",
    operateObject = "",
    success = "",
    fail = "",
    ignoreException = {},
    condition = ""
)
public @interface SimpleOperateLog {
    
    @AliasFor(annotation = OperateLog.class, attribute = "bizType")
    String bizType();

    @AliasFor(annotation = OperateLog.class, attribute = "subBizType")
    String subBizType() default "";

    @AliasFor(annotation = OperateLog.class, attribute = "operateObject")
    String operateObject() default "";

    @AliasFor(annotation = OperateLog.class, attribute = "success")
    String operateDesc();
}
