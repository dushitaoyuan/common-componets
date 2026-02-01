package com.taoyuanx.common.audit.log.diff;

/**
 * 对象对比器
 *
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/8/6 21:05
 */
public interface ObjectDiffHandler {

    Object diff(Object base, Object after);
}
