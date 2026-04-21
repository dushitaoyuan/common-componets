package com.taoyuanx.common.audit.log.runtime.util;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.model.AuditLogModel;

import java.util.Date;
import java.util.List;

/**
 * 日志模型工具类
 * 统一处理 AuditLogModel 的序列化和反序列化
 *
 * @author lianglei78
 * @date 2026/4/21 13:55
 */
public class LogUtil {

    /**
     * 序列化日志对象
     */
    public static String logToString(AuditLogModel logModel) {
        return JSON.toJSONString(logModel);
    }

    public static String logToString(List<AuditLogModel> logModel) {
        return JSON.toJSONString(logModel);
    }


    /**
     * 反序列化日志对象
     */
    public static AuditLogModel stringToLog(String json) {
        return JSON.parseObject(json, AuditLogModel.class);
    }

    /**
     * 格式化日期
     */
    public static String fmtDate(Date date, String pattern) {

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern);
        return sdf.format(date);
    }

}
