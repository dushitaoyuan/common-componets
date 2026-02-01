package com.taoyuanx.common.audit.log.common;

import com.taoyuanx.common.audit.log.diff.ObjectDiffHandler;
import com.taoyuanx.common.audit.log.diff.handler.NoDiffHandler;

import java.lang.annotation.*;

/**
 * @author taoyuan date 2024/12/23 14:08 description 日志注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface LogDiff {

    // 操作前数据获取表达式

    String before() default "";


    // 操作后数据获取表达式
    String after() default "";

    // 对象对比器
    Class<? extends ObjectDiffHandler> diffHandler() default NoDiffHandler.class;

}
