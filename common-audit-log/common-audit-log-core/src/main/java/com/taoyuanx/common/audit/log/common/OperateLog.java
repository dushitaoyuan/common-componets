package com.taoyuanx.common.audit.log.common;

import java.lang.annotation.*;

/**
 * @author taoyuan date 2024/12/23 14:08 description 日志注解
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface OperateLog {


    // 操作对象获取表达式

    String operateObject() default "";

    // 业务类型
    String bizType();


    // 子业务类型
    String subBizType() default "";

    // 操作成功记录的日志模版
    String success();

    // 操作失败记录的日志模版
    String fail() default "";

    // 忽略异常
    Class<? extends Throwable>[] ignoreException() default {};

    // 记录条件
    String condition() default "";
}
