package com.taoyuanx.common.log.web;

import com.feiniaojin.gracefulresponse.data.PageBean;
import com.taoyuanx.common.audit.log.runtime.ext.AuditLogQueryService;
import com.taoyuanx.common.audit.log.runtime.model.*;
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
    public PageBean<AuditLogViewModel> page(@RequestBody PageQueryModel<AuditLogQueryModel> pageQuery) {
        // 设置默认分页参数
        if (pageQuery.getPageNum() == null || pageQuery.getPageNum() <= 0) {
            pageQuery.setPageNum(1);
        }
        if (pageQuery.getPageSize() == null || pageQuery.getPageSize() <= 0) {
            pageQuery.setPageSize(10);
        }
        PageModel<AuditLogViewModel> logs = auditLogQueryService.page(pageQuery);
        Long total = logs.getTotal();
        // 构造返回结果
        PageBean<AuditLogViewModel> result = new PageBean<>();
        result.setPage((int) (total + pageQuery.getPageSize() - 1) / pageQuery.getPageSize());
        result.setPageSize(pageQuery.getPageSize());
        result.setTotal(total.intValue());
        result.setList(logs.getList());
        return result;
    }

    @GetMapping("/detail")
    public AuditLogViewModel detail(Long logId, String tenant) {
        return auditLogQueryService.detail(logId,tenant);
    }

    /**
     * 滚动查询审计日志（支持向前/向后双向滚动）
     *
     * @param request 滚动查询请求
     * @return 滚动结果
     */
    @PostMapping("/scroll-query")
    public ScrollQueryResult scrollQuery(@RequestBody ScrollQueryRequest request) {
        if (request.getLimit() == null || request.getLimit() <= 0 || request.getLimit() > 100) {
            request.setLimit(30);
        }
        if (request.getDirection() == null) {
            request.setDirection("forward");
        }

        return auditLogQueryService.scrollQuery(request);
    }

}
