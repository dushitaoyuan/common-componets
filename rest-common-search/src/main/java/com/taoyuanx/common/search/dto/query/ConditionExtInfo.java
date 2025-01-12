package com.taoyuanx.common.search.dto.query;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 条件扩展参数
 *
 * @author dushitaoyuan
 * @date 2025/1/10 17:57
 */
@Data
public class ConditionExtInfo implements Serializable {

    /**
     * composeType 条件组合方式
     */
    public static final String COMPOSE_TYPE_AND = "AND", COMPOSE_TYPE_OR = "OR";
    /**
     * conditionType 条件类型
     */
    public static final String CONDITION_TYPE_SIMPLE = "simple", CONDITION_TYPE_COMPLEX = "complex";
    /**
     * 条件值类型
     */
    public static final String VALUE_TYPE_NUMBER = "number", VALUE_TYPE_TEXT = "text";


    /**
     * 操作符 默认 "=",
     * 支持: =,!=,>,>=,<,<=,like,in,not in,between and
     * @see ConditionOperateEnum
     */
    private String operate;
    /**
     * 字段类型: number,text
     * 默认text
     */
    private String valueType;
    /**
     * 条件类型: simple(单一条件),complex(复合条件,根据subConditions 组合)
     * 默认: simple
     */
    private String type;
    /**
     * 子条件
     */
    private List<Condition> subConditions;
    /**
     * 条件组合关系: AND,OR
     * 默认:AND
     */
    private String composeType;
}
