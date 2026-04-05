package com.taoyuanx.common.audit.log.runtime.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author taoyuan date 2024/12/23 13:48 description 操作日志
 */
@NoArgsConstructor
@Data
public class AuditLogViewModel implements Serializable {
    /**
     * id
     */
    private String id;
    /**
     * traceId
     */
    private String traceId;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 操作对象
     */
    private String operateObject;

    /**
     * 业务类型
     */
    private String bizType;
    /**
     * 操作类型
     */
    private String subType;
    /**
     * 操作描述
     */
    private String operateDesc;

    /**
     * 操作时间
     */
    private Long operateTime;

    /**
     * 操作详情
     */
    private Object operateDsl;

    /**
     * 租户标识
     */
    private String tenant;

    /**
     * 执行成功或异常
     */
    private Boolean success;
    /**
     * 耗时(毫秒)
     */
    private Long costTime;
    /**
     * 异常信息
     */
    private String errorMsg;
    /**
     * 操作日期
     */
    private String opDate;
    /**
     * 扩展信息
     */
    private Object ext;

}
