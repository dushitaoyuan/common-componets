package com.taoyuanx.common.log.web;

import com.feiniaojin.gracefulresponse.data.PageBean;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.model.AuditLogQueryModel;
import com.taoyuanx.common.audit.log.model.PageModel;
import com.taoyuanx.common.audit.log.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志查询 Controller
 */
@RestController
@RequestMapping("/api/audit/logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     *
     * @param queryModel 查询条件
     * @return 分页结果
     */
    @PostMapping("/page")
    public PageBean<AuditLogModel> page(@RequestBody AuditLogQueryModel queryModel) {
        // 设置默认分页参数
        if (queryModel.getPageNum() == null || queryModel.getPageNum() <= 0) {
            queryModel.setPageNum(1);
        }
        if (queryModel.getPageSize() == null || queryModel.getPageSize() <= 0) {
            queryModel.setPageSize(10);
        }
        PageModel<AuditLogModel> logs = auditLogService.page(queryModel);
        Long total = logs.getTotal();
        // 构造返回结果
        PageBean<AuditLogModel> result = new PageBean<>();
        result.setPage((int) (total + queryModel.getPageSize() - 1) / queryModel.getPageSize());
        result.setPageSize(queryModel.getPageSize());
        result.setTotal(total.intValue());
        result.setList(logs.getList());
        return result;
    }

    @GetMapping("/detail")
    public AuditLogModel detail(Long logId) {
        return auditLogService.detail(logId);
    }

}
