package com.taoyuanx.common.search.dal.model.query;

import com.taoyuanx.common.search.dto.query.SortCondition;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author dushitaoyuan
 * @date 2025/1/11 11:26
 */
@Data
public class UserQueryVO implements Serializable {
    private String name;
    private Integer age;
    private Long id;

    private List<Long> ids;

    private List<SortCondition> sorts;
}
