package com.taoyuanx.common.audit.log.runtime.collect.lmax;

import com.lmax.disruptor.EventFactory;

/**
 * LMAX Disruptor 事件工厂
 *
 * @author taoyuan
 * @date 2025/3/11
 */
public class AuditLogEventFactory implements EventFactory<AuditLogEvent> {
    @Override
    public AuditLogEvent newInstance() {
        return new AuditLogEvent();
    }
}