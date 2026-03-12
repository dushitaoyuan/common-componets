package com.taoyuanx.common.audit.log.context;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author taoyuan @Date 2024/12/23 15:05 @Description 日志上下文
 */
public class AuditLogContextUtil {
    // 日志上下文
    public static final String CONTEXT_KEY_OPERATOR = "operator";
    public static final String CONTEXT_KEY_TENANT = "tenant";
    /*
     * 操作对象 diff上下文
     */
    public static final String CONTEXT_KEY_BEFORE_OBJECT = "before";
    public static final String CONTEXT_KEY_AFTER_OBJECT = "after";

    public static final String CONTEXT_KEY_OPERATE_DSL = "operate_dsl";

    public static final String CONTEXT_KEY_TRACE_ID = "trace_id";

    public static final String CONTEXT_KEY_EXT = "ext";



    private static  LogThreadLocal<Map<String, Object>> CONTEXT=null;

    public static void initLocal(boolean allowNest){
        CONTEXT  = new LogThreadLocal<>(allowNest);
    }

    public static void remove() {
        CONTEXT.remove();
    }

    public static Map<String, Object> init() {
        Map<String, Object> value = new HashMap<>();
        CONTEXT.set(value);
        return value;
    }


    public static Map<String, Object> get() {
        return CONTEXT.get();
    }

    public static <T> T get(String contextKey, Map<String, Object> contextMap) {
        if (contextMap == null) {
            return null;
        }
        return (T) contextMap.get(contextKey);
    }

    public static <T> T get(String contextKey) {
        Map<String, Object> contextMap = get();
        if (contextMap == null) {
            return null;
        }
        return (T) contextMap.get(contextKey);
    }

    public static void set(String contextKey, Object value) {
        Map<String, Object> contextMap = get();
        if (contextMap == null) {
            contextMap = init();
        }
        if (value == null) {
            contextMap.remove(contextKey);
        } else {
            contextMap.put(contextKey, value);
        }
    }
}
