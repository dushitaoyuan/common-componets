package com.taoyuanx.common.audit.log.runtime.util;

import java.util.zip.CRC32;

/**
 * 分表工具类
 * <p>
 * 根据租户标识（tenant）或 ID 计算分表索引，支持分表名生成
 * </p>
 *
 * @author taoyuan
 * @date 2025/7/29
 */
public class ShardingUtil {

    /**
     * 根据 tenant 计算 op_log 分表索引
     * 使用 crc32 hash 取模的方式计算
     *
     * @param tenant     租户标识
     * @param tableCount 分表数量
     * @return 分表索引 (0 到 tableCount-1)
     */
    public static int calcTableIndex(String tenant, int tableCount) {
        if (tenant == null) {
            return 0;
        }
        if (tableCount <= 1) {
            return 0;
        }
        // 使用 CRC32 计算哈希值，然后取模
        CRC32 crc32 = new CRC32();
        crc32.update(tenant.getBytes());
        long hash = crc32.getValue();
        int index = (int) (hash % tableCount);
        return index;
    }

    /**
     * 根据分表索引生成主表名
     *
     * @param tableNamePrefix 表名前缀
     * @param tableIndex      分表索引
     * @return 实际表名
     */
    public static String generateTableName(String tableNamePrefix, int tableIndex) {
        return tableNamePrefix + "_" + tableIndex;
    }

}