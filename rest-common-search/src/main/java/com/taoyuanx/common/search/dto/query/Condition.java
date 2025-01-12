package com.taoyuanx.common.search.dto.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;


/**
 * 条件搜索
 *
 * @author dushitaoyuan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Condition implements Serializable {
    /**
     * 字段名称
     */

    private String name;
    /**
     * 字段值
     */
    private String value;
    /**
     * 搜索条件扩展参数
     */
    private ConditionExtInfo extInfo;
}
