package com.taoyuanx.common.audit.log.diff.handler;

import com.taoyuanx.common.audit.log.diff.ObjectDiffHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认对象对比器( 不对比,只缓存前后结果,可在展示端对比)
 *
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/8/6 21:06
 */
public class NoDiffHandler implements ObjectDiffHandler {

    public static final NoDiffHandler NO_DIFF_HANDLER = new NoDiffHandler();

    @Override
    public Object diff(Object base, Object after) {
        if (base == null && after == null) {
            return null;
        }
        Map<String, Object> diffMap = new HashMap<>();
        if (base != null) {
            diffMap.put("before", base);
        }
        if (after != null) {
            diffMap.put("after", after);
            return diffMap;
        }
        return diffMap;
    }

}
