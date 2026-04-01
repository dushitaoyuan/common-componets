package com.taoyuanx.common.audit.log.runtime.ext;

import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.runtime.model.PageModel;
import com.taoyuanx.common.audit.log.runtime.model.PageQueryModel;

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
    PageModel<AuditLogModel> page(PageQueryModel<AuditLogQueryModel> pageQuery);

    /**
     * 根据日志ID查询详情
     *
     * @param logId 日志ID
     * @return 日志详情
     */
    AuditLogModel detail(Long logId, String tenant);


}
