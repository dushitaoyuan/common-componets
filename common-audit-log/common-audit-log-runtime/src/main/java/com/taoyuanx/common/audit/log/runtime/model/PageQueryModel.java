package com.taoyuanx.common.audit.log.runtime.model;

import lombok.Data;

import java.util.List;

/**
 * 分页对象
 *
 * <p></p>
 *
 * @author taoyuan::wq
 * @date 2025/8/27 16:48
 */
@Data
public class PageQueryModel<T> {
    /**
     * 页码
     */
    private Integer pageSize;
    /**
     * 页大小
     */
    private Integer pageNum;
    /**
     * 查询体
     */
    private T query;


}
