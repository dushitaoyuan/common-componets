package com.taoyuanx.common.log.web.demo;

import com.taoyuanx.common.audit.log.common.LogDiff;
import com.taoyuanx.common.audit.log.common.OperateLog;
import com.taoyuanx.common.audit.log.common.SimpleOperateLog;
import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志示例demo
 */
@RestController
@RequestMapping("/api/test/auditLog")
public class AuditLogDemoController {
    @Autowired
    private DemoLogService demoLogService;

    @PostMapping("/logSimple")
    @ResponseBody
    @OperateLog(
            success = "'新增了:' +#params.get('flowBizNo')+ ',操作结果:' + #result.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#params.get('flowBizNo')")
    public Map logSimple(@RequestBody Map<String, Object> params) throws IOException {
        return params;
    }

    @PostMapping("/logSimple2")
    @ResponseBody
    @SimpleOperateLog(
            operateDesc = "'新增了:' +#params.get('flowBizNo')+ ',操作结果:' + #result.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#params.get('flowBizNo')")
    public Map logSimple2(@RequestBody Map<String, Object> params) throws IOException {
        return params;
    }


    @PostMapping("/logWithDetail")
    @ResponseBody
    @OperateLog(
            success = "'新增了:' +#params.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#result.get('addObject').get('name')")
    @LogDiff(after = "#result.get('addObject')")
    public Map logWithDetail(@RequestBody Map<String, Object> params) throws IOException {
        return params;
    }

    @PostMapping("/logWithDelete")
    @ResponseBody
    @OperateLog(
            success = "'删除了:' +#params.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#result.get('deleteObject').get('name')")
    @LogDiff(before = "#result.get('deleteObject')")
    public Map logWithDelete(@RequestBody Map<String, Object> params) throws IOException {
        return params;
    }

    @PostMapping("/logWithUpdate")
    @ResponseBody
    @OperateLog(
            success = "'更新了:' +#params.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#result.get('name')")
    public Map logWithUpdate(@RequestBody Map<String, Object> params) throws IOException {
        AuditLogContextUtil.set(
                AuditLogContextUtil.CONTEXT_KEY_BEFORE_OBJECT, params.get("before"));
        AuditLogContextUtil.set(
                AuditLogContextUtil.CONTEXT_KEY_AFTER_OBJECT, params.get("after"));
        return params;
    }

    @PostMapping("/logWithCustomDetail")
    @ResponseBody
    @OperateLog(
            success = "'自定义日志详情:' +#params.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#result.get('name')")
    public Map logWithCustomDetail(@RequestBody Map<String, Object> params) throws IOException {
        AuditLogContextUtil.set(
                AuditLogContextUtil.CONTEXT_KEY_OPERATE_DSL, params.get("detail"));
        return params;
    }
    @PostMapping("/logWithExt")
    @ResponseBody
    @OperateLog(
            success = "'自定义日志详情:' +#params.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#result.get('name')")
    public Map logWithExt(@RequestBody Map<String, Object> params) throws IOException {
        AuditLogContextUtil.set(
                AuditLogContextUtil.CONTEXT_KEY_OPERATE_DSL, params.get("detail"));
        AuditLogContextUtil.set(AuditLogContextUtil.CONTEXT_KEY_EXT, params.get("ext"));
        return params;
    }

    @PostMapping("/logConditional")
    @ResponseBody
    @OperateLog(
            success = "'自定义日志详情:' +#params.get('flowBizNo')",
            bizType = "测试",
            operateObject = "#result.get('name')",
            condition = "#result.get('condition') == true")
    public Map logConditional(@RequestBody Map<String, Object> params) throws IOException {
        return params;
    }

    @GetMapping("/nestLogTest")
    @ResponseBody
    @OperateLog(
            success = "'嵌套日志层级:' +#operateObject",
            bizType = "嵌套日志测试",
            operateObject = "#operateObject")
    public Map nestLogTest(Long operateObject) throws IOException {
        demoLogService.businessLog(2L);
        return new HashMap();
    }


    @PostMapping("/logWithError")
    @ResponseBody
    @SimpleOperateLog(
            operateDesc = "'异常日志:' +#params.get('flowBizNo')",
            bizType = "异常日志")
    public Map logWithError(@RequestBody Map<String, Object> params) throws IOException {
        throw new RuntimeException("测试异常日志");
    }
    @PostMapping("/logWithTrace")
    @ResponseBody
    @SimpleOperateLog(
            operateDesc = "traceId测试",
            bizType = "trace")
    public Map logWithTrace(@RequestBody Map<String, Object> params) throws IOException {
        AuditLogContextUtil.set(
                AuditLogContextUtil.CONTEXT_KEY_TRACE_ID, params.get("traceId"));
        return new HashMap();
    }
}
