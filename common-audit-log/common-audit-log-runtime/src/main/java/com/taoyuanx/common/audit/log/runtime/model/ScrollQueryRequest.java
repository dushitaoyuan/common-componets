package com.taoyuanx.common.audit.log.runtime.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 滚动查询请求参数
 *
 * @author taoyuan
 * @date 2026/4/1
 */
@Data
@NoArgsConstructor
public class ScrollQueryRequest {
    /**
     * 当前游标 ID（首次查询可不传或传 0）
     */
    private Long cursorId;
    
    /**
     * 游标对应的时间戳（可选，如果提供可减少一次数据库查询）
     */
    private Long cursorTime;
    
    /**
     * 滚动方向：forward-向前（加载更新的），backward-向后（加载更旧的）
     */
    private String direction = "forward";
    
    /**
     * 每次加载条数
     */
    private Integer limit = 30;
    
    /**
     * 查询条件
     */
    private AuditLogQueryModel query;
}
