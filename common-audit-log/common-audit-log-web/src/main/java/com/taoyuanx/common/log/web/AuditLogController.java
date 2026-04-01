package com.taoyuanx.common.log.web;

import com.feiniaojin.gracefulresponse.data.PageBean;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.runtime.ext.AuditLogQueryService;
import com.taoyuanx.common.audit.log.runtime.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.runtime.model.PageModel;
import com.taoyuanx.common.audit.log.runtime.model.PageQueryModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志查询 Controller
 */
@RestController
@RequestMapping("/api/audit/logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    /**
     * 分页查询审计日志
     *
     * @param pageQuery 查询条件
     * @return 分页结果
     */
    @PostMapping("/page")
    public PageBean<AuditLogModel> page(@RequestBody PageQueryModel<AuditLogQueryModel> pageQuery) {
        // 设置默认分页参数
        if (pageQuery.getPageNum() == null || pageQuery.getPageNum() <= 0) {
            pageQuery.setPageNum(1);
        }
        if (pageQuery.getPageSize() == null || pageQuery.getPageSize() <= 0) {
            pageQuery.setPageSize(10);
        }
        PageModel<AuditLogModel> logs = auditLogQueryService.page(pageQuery);
        Long total = logs.getTotal();
        // 构造返回结果
        PageBean<AuditLogModel> result = new PageBean<>();
        result.setPage((int) (total + pageQuery.getPageSize() - 1) / pageQuery.getPageSize());
        result.setPageSize(pageQuery.getPageSize());
        result.setTotal(total.intValue());
        result.setList(logs.getList());
        return result;
    }

    @GetMapping("/detail")
    public AuditLogModel detail(Long logId,String tenant) {
        return auditLogQueryService.detail(logId,tenant);
    }

}
