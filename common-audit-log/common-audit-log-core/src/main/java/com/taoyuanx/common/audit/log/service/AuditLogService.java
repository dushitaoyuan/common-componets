package com.taoyuanx.common.audit.log.service;

import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.context.AuditLogContextUtil;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.util.AuditLogUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * 日志快速记录服务
 * <p>提供便捷的日志记录方法，覆盖常用场景</p>
 */
@Slf4j
public class AuditLogService {
    private AuditLogCollector auditLogCollector;
    private List<AuditLogFillHandler> logFillHandlers;

    public AuditLogService(AuditLogCollector auditLogCollector, List<AuditLogFillHandler> logFillHandlers) {
        this.auditLogCollector = auditLogCollector;
        this.logFillHandlers = logFillHandlers;
    }

    /**
     * 收集日志（底层方法）
     *
     * @param auditLogModel 日志对象
     */
    public void collect(AuditLogModel auditLogModel) {
        try {
            AuditLogUtil.mergeLogContext(logFillHandlers,auditLogModel);
            auditLogCollector.collect(auditLogModel);
        } catch (Exception e) {
            log.error("Collect audit log error", e);
        }
    }

    /**
     * 记录简单成功日志
     * <p>适用场景: 简单的操作记录，只需要业务类型、操作类型和描述</p>
     * <p>例如: 用户登录、订单创建、数据删除等</p>
     *
     * @param bizType     业务类型
     * @param subType     操作类型
     * @param operateDesc 操作描述
     */
    public void logSuccess(String bizType, String subType, String operateDesc) {
        AuditLogModel logModel = createBasicLog(bizType, subType, operateDesc, true, null);
        collect(logModel);
    }

    /**
     * 记录带操作对象的成功日志
     * <p>适用场景: 需要记录具体操作了哪个对象</p>
     * <p>例如: 修改用户信息、删除某个订单、查询某条记录</p>
     *
     * @param bizType       业务类型
     * @param subType       操作类型
     * @param operateObject 操作对象（如用户ID、订单号等）
     * @param operateDesc   操作描述
     */
    public void logSuccessWithObject(String bizType, String subType,
                                      String operateObject, String operateDesc) {
        AuditLogModel logModel = createBasicLog(bizType, subType, operateDesc, true, null);
        logModel.setOperateObject(operateObject);
        collect(logModel);
    }

    /**
     * 记录失败日志
     * <p>适用场景: 记录操作失败的情况，包含错误信息</p>
     * <p>例如: 接口调用失败、数据处理异常、权限校验失败</p>
     *
     * @param bizType     业务类型
     * @param subType     操作类型
     * @param operateDesc 操作描述
     * @param errorMsg    错误信息
     */
    public void logError(String bizType, String subType,
                         String operateDesc, String errorMsg) {
        AuditLogModel logModel = createBasicLog(bizType, subType, operateDesc, false, errorMsg);
        collect(logModel);
    }


    /**
     * 记录带扩展信息的日志
     * <p>适用场景: 需要记录额外的业务数据</p>
     * <p>例如: 记录操作前后的值、IP地址、设备信息等</p>
     *
     * @param bizType     业务类型
     * @param subType     操作类型
     * @param operateDesc 操作描述
     * @param ext         扩展信息（可以是Map、JSON字符串等）
     */
    public void logWithExt(String bizType, String subType,
                           String operateDesc, Object ext) {
        AuditLogModel logModel = createBasicLog(bizType, subType, operateDesc, true, null);
        logModel.setExt(ext);
        collect(logModel);
    }
    /**
     * 记录带扩展信息的日志
     * <p>适用场景: 记录操作前后的值</p>
     *
     * @param bizType     业务类型
     * @param subType     操作类型
     * @param operateDesc 操作描述
     * @param dsl         扩展信息（可以是Map、JSON字符串等）
     */
    public void logWithDsl(String bizType, String subType,
                           String operateDesc, Object dsl) {
        AuditLogModel logModel = createBasicLog(bizType, subType, operateDesc, true, null);
        logModel.setOperateDsl(dsl);
        collect(logModel);
    }

    /**
     * 记录完整的自定义日志
     * <p>适用场景: 需要完全控制所有字段</p>
     * <p>例如: 复杂的业务流程、需要记录耗时等</p>
     *
     * @param logBuilder 日志构建器（Consumer函数）
     */
    public void logCustom(Consumer<AuditLogModel> logBuilder) {
        AuditLogModel logModel = new AuditLogModel();
        // 设置默认值
        logModel.setOperateTime(System.currentTimeMillis());
        logModel.setSuccess(true);
        
        // 允许用户自定义
        logBuilder.accept(logModel);
        
        collect(logModel);
    }

    /**
     * 创建基础日志对象
     *
     * @param bizType     业务类型
     * @param subType     操作类型
     * @param operateDesc 操作描述
     * @param success     是否成功
     * @param errorMsg    错误信息
     * @return 日志对象
     */
    private AuditLogModel createBasicLog(String bizType, String subType,
                                         String operateDesc, Boolean success,
                                         String errorMsg) {
        AuditLogModel logModel = new AuditLogModel();
        logModel.setBizType(bizType);
        logModel.setSubType(subType);
        logModel.setOperateDesc(operateDesc);
        logModel.setSuccess(success);

        if (errorMsg != null) {
            logModel.setErrorMsg(errorMsg);
        }
        return logModel;
    }
}
