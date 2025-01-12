package com.taoyuanx.common.search.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taoyuanx.common.search.dto.page.PageResult;

/**
 * @author dushitaoyuan
 * @date 2025/1/11 11:41
 */
public class PageUtil {


    public static <T> PageResult<T> toPageResult(Page<T> queryPageResult) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setTotal(queryPageResult.getTotal());
        pageResult.setCurrentPage((int) queryPageResult.getCurrent());
        pageResult.setPageSize((int) queryPageResult.getPages());
        pageResult.setList(queryPageResult.getRecords());
        return pageResult;
    }
}
