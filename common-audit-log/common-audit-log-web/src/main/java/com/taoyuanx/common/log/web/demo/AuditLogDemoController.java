package com.taoyuanx.common.log.web.demo;

import com.taoyuanx.common.audit.log.common.LogDiff;
import com.taoyuanx.common.audit.log.common.OperateLog;
import com.taoyuanx.common.audit.log.common.SimpleOperateLog;
import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志示例demo
 */
@RestController
@RequestMapping("/api/test/auditLog")
public class AuditLogDemoController {
    @Autowired
    private DemoLogService demoLogService;
    
    @Autowired
    private AuditLogService auditLogService;

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
    public Map logWithExtAnno(@RequestBody Map<String, Object> params) throws IOException {
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
    
    // ==================== 直接记录日志示例（AuditLogService） ====================
    
    /**
     * 示例1: 简单成功日志
     */
    @PostMapping("/direct/logSuccess")
    @ResponseBody
    public Map logSuccess(@RequestBody Map<String, Object> params) {
        // 记录简单成功日志
        auditLogService.logSuccess("USER", "LOGIN", "用户登录成功: " + params.get("username"));
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "日志记录成功");
        return result;
    }
    
    /**
     * 示例2: 带操作对象的成功日志
     */
    @PostMapping("/direct/logSuccessWithObject")
    @ResponseBody
    public Map logSuccessWithObject(@RequestBody Map<String, Object> params) {
        String userId = (String) params.get("userId");
        String action = (String) params.getOrDefault("action", "修改用户信息");
        
        // 记录带操作对象的日志
        auditLogService.logSuccessWithObject("USER", "UPDATE", userId, action);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "日志记录成功");
        return result;
    }
    
    /**
     * 示例3: 失败日志
     */
    @PostMapping("/direct/logError")
    @ResponseBody
    public Map logError(@RequestBody Map<String, Object> params) {
        try {
            // 模拟业务异常
            if ("true".equals(params.get("simulateError"))) {
                throw new RuntimeException("模拟业务异常：数据校验失败");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "操作成功");
            return result;
        } catch (Exception e) {
            // 记录失败日志
            auditLogService.logError("ORDER", "CREATE", 
                "创建订单失败", e.getMessage());
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("message", "操作失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 示例4: 带扩展信息的日志
     * ext用于记录业务元数据（如IP、设备信息、统计信息等）
     */
    @PostMapping("/direct/logWithExt")
    @ResponseBody
    public Map logWithExt(@RequestBody Map<String, Object> params) {
        // 构建业务扩展信息（元数据）
        Map<String, Object> ext = new HashMap<>();
        ext.put("ip", params.get("ip"));
        ext.put("deviceId", params.get("deviceId"));
        ext.put("userAgent", params.get("userAgent"));
        ext.put("timestamp", System.currentTimeMillis());
        
        // 记录带扩展信息的日志
        auditLogService.logWithExt("USER", "LOGIN", 
            "用户登录成功", ext);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "日志记录成功");
        return result;
    }
    

    
    /**
     * 示例5: 自定义日志（完整控制）
     * operate_dsl: 记录操作详情/变更数据
     * ext: 记录业务元数据
     */
    @PostMapping("/direct/logCustom")
    @ResponseBody
    public Map logCustom(@RequestBody Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        
        // 模拟业务处理
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long costTime = System.currentTimeMillis() - startTime;
        
        // 记录自定义日志
        auditLogService.logCustom(log -> {
            log.setBizType("USER");
            log.setSubType("UPDATE");
            log.setOperateDesc("更新用户资料");
            log.setOperator((String) params.get("operator"));
            log.setCostTime(costTime);
            log.setSuccess(true);
            
            // operate_dsl: 记录变更详情（操作前后的数据对比）
            Map<String, Object> operateDsl = new HashMap<>();
            operateDsl.put("before", params.get("beforeData"));
            operateDsl.put("after", params.get("afterData"));
            log.setOperateDsl(operateDsl);
            
            // ext: 记录业务元数据
            Map<String, Object> ext = new HashMap<>();
            ext.put("ip", params.get("ip"));
            ext.put("requestId", params.get("requestId"));
            ext.put("duration", costTime + "ms");
            log.setExt(ext);
        });
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "日志记录成功");
        result.put("costTime", costTime);
        return result;
    }
    
    /**
     * 示例6: 综合业务场景 - 订单创建
     * operate_dsl: 记录订单详情（变更数据）
     * ext: 记录业务元数据（请求上下文）
     */
    @PostMapping("/direct/createOrder")
    @ResponseBody
    public Map createOrder(@RequestBody Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 模拟订单创建逻辑
            String orderNo = "ORD" + System.currentTimeMillis();
            Double amount = Double.parseDouble(params.getOrDefault("amount", "99.99").toString());
            
            // 模拟数据库操作
            Thread.sleep(50);
            
            long costTime = System.currentTimeMillis() - startTime;
            
            // 构建订单详情（operate_dsl: 变更数据）
            Map<String, Object> orderDetail = new HashMap<>();
            orderDetail.put("orderNo", orderNo);
            orderDetail.put("productId", params.get("productId"));
            orderDetail.put("productName", params.get("productName"));
            orderDetail.put("amount", amount);
            orderDetail.put("quantity", params.get("quantity"));
            
            // 构建业务元数据（ext: 请求上下文）
            Map<String, Object> ext = new HashMap<>();
            ext.put("ip", params.get("ip"));
            ext.put("userAgent", params.get("userAgent"));
            ext.put("costTime", costTime);
            ext.put("source", params.get("source")); // 来源：APP/H5/PC
            
            // 记录日志
            auditLogService.logCustom(log -> {
                log.setBizType("ORDER");
                log.setSubType("CREATE");
                log.setOperateDesc("创建订单: " + orderNo);
                log.setOperateObject(orderNo);
                log.setSuccess(true);
                log.setCostTime(costTime);
                log.setOperateDsl(orderDetail);  // 订单详情（变更数据）
                log.setExt(ext);                  // 业务元数据
            });
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "订单创建成功");
            result.put("orderNo", orderNo);
            result.put("costTime", costTime);
            return result;
            
        } catch (Exception e) {
            // 记录失败日志
            auditLogService.logError("ORDER", "CREATE", 
                "创建订单失败", e.getMessage());
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("message", "订单创建失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 示例7: 批量处理场景
     * operate_dsl: 记录批量处理的详细结果
     * ext: 记录业务元数据（执行上下文）
     */
    @PostMapping("/direct/batchProcess")
    @ResponseBody
    public Map batchProcess(@RequestBody Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        
        // 模拟批量处理
        int totalCount = Integer.parseInt(params.getOrDefault("count", "100").toString());
        int successCount = 0;
        int failCount = 0;
        List<String> failedItems = new java.util.ArrayList<>();
        
        for (int i = 0; i < totalCount; i++) {
            String itemId = "item_" + i;
            // 模拟处理逻辑
            if (Math.random() > 0.05) { // 5%失败率
                successCount++;
            } else {
                failCount++;
                failedItems.add(itemId);
            }
        }
        
        long costTime = System.currentTimeMillis() - startTime;
        
        // 构建处理详情（operate_dsl: 变更数据/处理结果）
        Map<String, Object> processDetail = new HashMap<>();
        processDetail.put("totalCount", totalCount);
        processDetail.put("successCount", successCount);
        processDetail.put("failCount", failCount);
        processDetail.put("successRate", String.format("%.2f%%", (successCount * 100.0 / totalCount)));
        if (!failedItems.isEmpty()) {
            processDetail.put("failedItems", failedItems); // 失败的项列表
        }
        
        // 构建业务元数据（ext: 执行上下文）
        Map<String, Object> ext = new HashMap<>();
        ext.put("batchNo", params.get("batchNo"));
        ext.put("operator", params.get("operator"));
        ext.put("costTime", costTime);
        ext.put("executeTime", System.currentTimeMillis());
        
        // 记录自定义日志
        int finalFailCount = failCount;
        auditLogService.logCustom(log -> {
            log.setBizType("BATCH");
            log.setSubType("PROCESS");
            log.setOperateDesc("批量处理订单");
            log.setCostTime(costTime);
            log.setSuccess(finalFailCount == 0);
            log.setOperateDsl(processDetail);  // 处理详情（变更数据）
            log.setExt(ext);                    // 业务元数据
        });
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "批量处理完成");
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("costTime", costTime);
        return result;
    }
}
