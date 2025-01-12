package com.taoyuanx.common.search.dto.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @Author dushitaoyuan
 * @Date 2024/10/22 14:53
 * @Description 查询条件
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchCondition implements Serializable {

    /**
     * 查询条件集合
     */
    private List<Condition> conditions;
    /**
     * 条件组合关系: AND,OR
     */
    private String composeType;

    /**
     * 排序条件集合
     */
    private List<SortCondition> sorts;
}
