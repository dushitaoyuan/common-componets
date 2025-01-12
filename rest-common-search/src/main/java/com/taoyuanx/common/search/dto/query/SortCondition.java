package com.taoyuanx.common.search.dto.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * @author dushitaoyuan
 * 排序条件
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SortCondition implements Comparable<SortCondition>, Serializable {


    /**
     * ORDER ASC
     */
    public static final String ASC = "asc";

    /**
     * ORDER DESC
     */
    public static final String DESC = "desc";

    /**
     * 字段名称
     */
    private String name;

    /**
     * 字段排序类型：asc-升序(默认)，desc-降序
     */
    private String type = ASC;


    private int order;


    @Override
    public int compareTo(SortCondition o) {
        if (o.getOrder() == this.getOrder()) {
            return 0;
        }
        if (this.getOrder() > o.getOrder()) {
            return 1;
        }
        return -1;
    }
}
