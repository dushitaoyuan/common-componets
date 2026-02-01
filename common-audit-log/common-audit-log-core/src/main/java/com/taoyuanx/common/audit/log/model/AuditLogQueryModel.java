package com.taoyuanx.common.audit.log.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 日志查询参数
 *
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/5/19 20:27
 */
@Data
@NoArgsConstructor
public class AuditLogQueryModel {
    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 子业务类型
     */
    private String subType;
    /**
     * 操作的开始时间
     */
    private Date startTime;
    /**
     * 操作的截止时间
     */
    private Date endTime;
    /**
     * 操作人
     */

    private String operator;
    /**
     * 操作对象
     */
    private String operateObject;
    /**
     * traceId
     */
    private String traceId;
    /**
     * 操作结果是否成功
     */
    private Boolean success;
    /**
     * 操作描述
     */
    private String operateDesc;
    /**
     * 租户标识
     */
    private String tenant;

    /**
     * 页码
     */
    private Integer pageSize;
    /**
     * 页大小
     */
    private Integer pageNum;
}
