package com.taoyuanx.common.audit.log.fallback;

import com.taoyuanx.common.audit.log.model.AuditLogModel;

import java.util.List;

/**
 * 日志降级接口
 *
 *
 * @author taoyuanx
 * @date 2026/4/21 09:54
 */
public interface FallBackWriter {
    /**
     * 日志降级写入
     * @param log 日志实体
     */
    void write(AuditLogModel log);

    /**
     * 日志降级写入
     * @param logs 日志实体。
     */
    void write(List<AuditLogModel> logs);

    /**
     * 资源释放
     */
    void close();


}
