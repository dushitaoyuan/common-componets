package com.taoyuanx.common.audit.log.runtime.collect;

import com.alibaba.fastjson2.JSON;
import com.taoyuanx.common.audit.log.collect.AuditLogCollector;
import com.taoyuanx.common.audit.log.common.LogException;
import com.taoyuanx.common.audit.log.model.AuditLogModel;
import com.taoyuanx.common.audit.log.pool.AuditLogModelPool;
import com.taoyuanx.common.audit.log.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;

/**
 * 直接收集
 *
 * @author taoyuan
 * @date 2025/7/29 18:19
 */
@Slf4j
public class AuditLogDirectCollector implements AuditLogCollector {
    private AuditLogService auditLogService;
    private AuditLogModelPool auditLogModelPool;


    public AuditLogDirectCollector(AuditLogService auditLogService, AuditLogModelPool auditLogModelPool) {
        this.auditLogService = auditLogService;
        this.auditLogModelPool = auditLogModelPool;
    }

    @Override
    public void collect(AuditLogModel auditLogModel) throws Exception {
        try {
            auditLogService.saveAuditLog(auditLogModel);
        }catch (Exception e){
            log.error("collect log error,operationLog:{}", JSON.toJSONString(auditLogModel),e);
            throw new LogException("direct collect log error",e);
        }finally {
            if (auditLogModelPool != null) {
                auditLogModelPool.returnObject(auditLogModel);
            }
        }
    }

    @Override
    public void close() {
        if(auditLogModelPool!=null){
            auditLogModelPool.close();
        }

    }
}
