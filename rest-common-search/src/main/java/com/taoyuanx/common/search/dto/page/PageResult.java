package com.taoyuanx.common.search.dto.page;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @Author  * @Date 2024/11/19 09:59
 * @Date 2024/10/22 14:50
 * @Description 分页返回值
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {
    /**
     * 总条数
     */
    private Long total;
    /**
     * 当前页数据
     */
    private List<T> list;
    /**
     * 分页大小 可选
     */
    private Integer pageSize;
    /**
     * 总页数 可选
     */
    private Long totalPage;

    /**
     * 当前页
     */
    private Integer currentPage;


    public PageResult(Long total, List<T> list) {
        this.total = total;
        this.list = list;
    }

    public Long getTotalPage() {
        if (total == null || total <= 0 || pageSize == null || pageSize <= 0) {
            return 0L;
        }
        if (totalPage != null) {
            return this.totalPage;
        }
        this.totalPage = total % pageSize == 0 ? total / pageSize : total / pageSize + 1;
        return this.totalPage;
    }
    public static <T> PageResult<T> newPageResult(PageResult pageResult, List<T> list){
        PageResult<T> newPageResult=new PageResult<T>();
        newPageResult.setCurrentPage(pageResult.getCurrentPage());
        newPageResult.setPageSize(pageResult.getPageSize());
        newPageResult.setTotalPage(pageResult.getTotalPage());
        newPageResult.setList(list);
        newPageResult.setTotal(pageResult.getTotal());
        return newPageResult;

    }
}
