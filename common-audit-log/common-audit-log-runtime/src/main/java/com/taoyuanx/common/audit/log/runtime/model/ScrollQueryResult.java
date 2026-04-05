package com.taoyuanx.common.audit.log.runtime.model;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 滚动查询结果
 *
 * @author taoyuan
 * @date 2026/4/1
 */
@Data
@NoArgsConstructor
public class ScrollQueryResult {
    /**
     * 数据列表
     */
    private List<AuditLogViewModel> list;
    
    /**
     * 下次查询的游标 ID
     */
    private String nextCursor;
    
    /**
     * 下次查询的游标时间戳（可选，前端可传递给后端减少查询）
     */
    private Long nextCursorTime;
}
