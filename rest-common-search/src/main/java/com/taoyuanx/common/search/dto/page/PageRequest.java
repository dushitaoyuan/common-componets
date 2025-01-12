package com.taoyuanx.common.search.dto.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageRequest<T> implements Serializable {
    /**
     * 页码
     */
    @Range(min = 1, message = "当前页最小为1")
    private Integer pageNum = 1;

    /**
     * 分页大小
     */
    @Range(min = 1, message = "每页展示条数最小为1")
    private Integer pageSize = 10;


    /**
     * 分页查询
     */
    private T query;
}
