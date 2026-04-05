package com.taoyuanx.common.audit.log.runtime.ext;

import com.taoyuanx.common.audit.log.runtime.model.*;

/**
 * 日志操作服务
 *
 * @author taoyuan
 * @date 2025/5/19 20:39
 */
public interface AuditLogQueryService {
    /**
     * 日志分页查询
     *
     * @param pageQuery 分页查询参数
     * @return
     */
    PageModel<AuditLogViewModel> page(PageQueryModel<AuditLogQueryModel> pageQuery);

    /**
     * 根据日志 ID 查询详情
     *
     * @param logId 日志 ID
     * @return 日志详情
     */
    AuditLogViewModel detail(Long logId, String tenant);

    /**
     * 滚动查询（支持向前/向后双向滚动）
     *
     * @param request 滚动查询请求
     * @return 滚动查询结果
     */
    ScrollQueryResult scrollQuery(ScrollQueryRequest request);


}
