package com.taoyuanx.common.audit.log.model;

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
public class PageModel<T> {
    private Long total;
    private List<T> list;

}
