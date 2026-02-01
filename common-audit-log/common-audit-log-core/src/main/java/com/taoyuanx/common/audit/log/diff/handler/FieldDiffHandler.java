package com.taoyuanx.common.audit.log.diff.handler;

import com.taoyuanx.common.audit.log.diff.DiffMapVisitor;
import com.taoyuanx.common.audit.log.diff.ObjectDiffHandler;
import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.node.DiffNode;


/**
 * 字段对比处理器
 *
 * <p></p>
 *
 * @author taoyuan
 * @date 2025/8/6 21:06
 */
public class FieldDiffHandler implements ObjectDiffHandler {


    @Override
    public Object diff(Object base, Object after) {
        DiffNode root = ObjectDifferBuilder.buildDefault().compare(base, after);
        DiffMapVisitor diffMapVisitor = new DiffMapVisitor(base, after);
        root.visit(diffMapVisitor);
        return diffMapVisitor.getDiffMap();
    }

}
