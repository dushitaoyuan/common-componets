package com.taoyuanx.common.audit.log.runtime.impl.log;

import com.taoyuanx.common.audit.log.common.LogIdGenerator;
import com.taoyuanx.common.audit.log.runtime.autoconfigure.AuditLogProperties;
import com.taoyuanx.common.audit.log.runtime.impl.AbstractJdbcTemplateAuditLogService;
import com.taoyuanx.common.audit.log.runtime.util.ShardingUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 分表审计日志服务实现
 * <p>
 * op_log 表根据 tenant 的 crc32 hash 取模计算分表
 * op_log_detail 表根据 ID 取模计算分表
 * </p>
 *
 * @author taoyuan
 * @date 2025/7/29
 */
public class ShardingAuditLogService extends AbstractJdbcTemplateAuditLogService {

    public ShardingAuditLogService(JdbcTemplate jdbcTemplate, AuditLogProperties auditLogProperties, LogIdGenerator logIdGenerator) {
        super(jdbcTemplate,auditLogProperties);
        setLogIdGenerator(logIdGenerator);
    }
    @Override
    protected String calcTableName(String tenant, String tablePrefix) {
        if (StringUtils.isBlank(tenant)) {
            throw new UnsupportedOperationException("分表模式下，详情表根据 ID 计算，查询时无法预知 ID");
        }
        int tableIndex = ShardingUtil.calcTableIndex(tenant, auditLogProperties.getShardingTableCount());
        return ShardingUtil.generateTableName(tablePrefix, tableIndex);
    }


}