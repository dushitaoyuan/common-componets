package com.taoyuanx.common.search.dto.query;

import java.util.Arrays;

/**
 * 搜索条件操作符
 *
 * @author dushitaoyuan
 * @date 2025/1/10 18:44
 */
public enum ConditionOperateEnum {
    EQUAL("等于", "="), NOT_EQUAL("不等于", "!="),
    GREATER_THAN("大于", ">"),
    GREATER_THAN_OR_EQUAL("大于等于", ">="),
    LESS_THAN("小于", "<"),
    LESS_THAN_OR_EQUAL("小于等于", "<="),
    LIKE("包含", "like %s", null),
    LEFT_LIKE("左包含", "like %s", null),
    RIGHT_LIKE("右包含", "like %s", null),

    IN("在范围之内", "in (%s)", null),
    NOT_IN("不在范围之内", "not in (%s)", null),
    BETWEEN_AND("在区间之内", "BETWEEN %s AND %s", null),
    ;
    // 操作符名称
    public final String name;
    // 操作符值
    public final String operate;
    // 操作符构建模版
    public final String fmt;

    ConditionOperateEnum(String name, String operate) {
        this(name, null, operate);

    }

    ConditionOperateEnum(String name, String fmt, String operate) {
        this.name = name;
        this.fmt = fmt;
        this.operate = operate;
    }

    public static ConditionOperateEnum getOperate(String enumName) {
        if (enumName == null) {
            return ConditionOperateEnum.EQUAL;
        }
        return Arrays.stream(ConditionOperateEnum.values()).filter(e -> e.name().equals(enumName)).findFirst().orElse(ConditionOperateEnum.EQUAL);
    }

}
