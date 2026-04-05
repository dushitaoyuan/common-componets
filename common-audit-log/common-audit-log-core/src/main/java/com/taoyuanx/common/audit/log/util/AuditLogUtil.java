package com.taoyuanx.common.audit.log.util;

import com.taoyuanx.common.audit.log.model.AuditLogModel;

public class AuditLogUtil {
    /**
     * 轻量级克隆AuditLogModel
     * 由于对象字段都是基本类型或String引用,浅拷贝即可
     */
    public static AuditLogModel cloneAuditLog(AuditLogModel source) {
        if (source == null) {
            return null;
        }

        AuditLogModel target = new AuditLogModel();
        target.setId(source.getId());
        target.setTraceId(source.getTraceId());
        target.setOperator(source.getOperator());
        target.setOperateObject(source.getOperateObject());
        target.setBizType(source.getBizType());
        target.setSubType(source.getSubType());
        target.setOperateDesc(source.getOperateDesc());
        target.setOperateTime(source.getOperateTime());
        target.setOperateDsl(source.getOperateDsl());
        target.setTenant(source.getTenant());
        target.setSuccess(source.getSuccess());
        target.setCostTime(source.getCostTime());
        target.setErrorMsg(source.getErrorMsg());
        target.setOpDate(source.getOpDate());
        target.setExt(source.getExt());

        return target;
    }

    public static void resetAuditLog(AuditLogModel model) {
        if (model == null) {
            return ;
        }
        model.setId(null);
        model.setTraceId(null);
        model.setOperator(null);
        model.setOperateObject(null);
        model.setBizType(null);
        model.setSubType(null);
        model.setOperateDesc(null);
        model.setOperateTime(null);
        model.setOperateDsl(null);
        model.setTenant(null);
        model.setSuccess(null);
        model.setCostTime(null);
        model.setErrorMsg(null);
        model.setOpDate(null);
        model.setExt(null);

    }

}
