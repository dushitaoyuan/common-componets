package com.taoyuanx.common.search.util;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.taoyuanx.common.search.dto.query.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 构造sql查询条件,将通用的搜索条件参数转为sql
 *
 * <p>
 * SearchConditionSqlUtil.buildConditionSql(searchCondition)
 * SearchConditionSqlUtil.buildSortCondition(searchCondition)
 * </p>
 *
 * @author dushitaoyuan
 * @date 2025/1/10 17:18
 */
@SuppressWarnings("all")
public class SearchConditionSqlUtil {

    public static String buildConditionSql(SearchCondition searchCondition, boolean withWhere) {
        if (searchCondition == null || CollectionUtils.isEmpty(searchCondition.getConditions())) {
            return "";
        }
        StringBuilder sqlBuilder = new StringBuilder();
        if (withWhere) {
            sqlBuilder.append(" WHERE ");
        } else {
            sqlBuilder.append(" ");
        }
        appendConditions(sqlBuilder, searchCondition.getConditions(), searchCondition.getComposeType());
        sqlBuilder.append(buildSortCondition(searchCondition.getSorts()));
        return sqlBuilder.toString();
    }

    private static void appendConditions(StringBuilder sqlBuilder, List<Condition> conditions, String composeType) {
        if (CollectionUtils.isEmpty(conditions)) {
            return;
        }
        composeType = StringUtils.equalsAny(composeType, ConditionExtInfo.COMPOSE_TYPE_AND, ConditionExtInfo.COMPOSE_TYPE_OR) ? composeType : ConditionExtInfo.COMPOSE_TYPE_AND;
        boolean first = true;
        for (Condition condition : conditions) {
            buildCondition(sqlBuilder, condition, composeType, first);
            first = false;
        }
    }

    private static void buildCondition(StringBuilder sqlBuilder, Condition condition, String composeType, boolean first) {
        ConditionExtInfo extInfo = condition.getExtInfo();
        String conditionType = Optional.ofNullable(extInfo).map(ConditionExtInfo::getType).orElse(ConditionExtInfo.CONDITION_TYPE_SIMPLE);
        if (!first) {
            sqlBuilder.append(" ").append(composeType).append(" ");
        }
        if (Objects.equals(conditionType, ConditionExtInfo.CONDITION_TYPE_SIMPLE)) {
            if (StringUtils.isEmpty(condition.getValue())) {
                return;
            }
            if (SqlCheckUtil.isSQLInjection(condition.getValue())) {
                throw new RuntimeException("条件参数非法");
            }
            sqlBuilder.append(getConditionSql(condition));
        } else if (Objects.equals(conditionType, ConditionExtInfo.CONDITION_TYPE_COMPLEX) && CollectionUtils.isNotEmpty(extInfo.getSubConditions())) {
            sqlBuilder.append("(");
            appendConditions(sqlBuilder, extInfo.getSubConditions(), extInfo.getComposeType());
            sqlBuilder.append(")");
        }
    }


    public static String buildSortCondition(List<SortCondition> sorts) {
        if (CollectionUtils.isEmpty(sorts)) {
            return "";
        }
        StringBuilder sqlBuilder = new StringBuilder();
        Collections.sort(sorts);
        sqlBuilder.append(" ORDER BY ");
        for (int i = 0; i < sorts.size(); i++) {
            SortCondition sort = sorts.get(i);
            sqlBuilder.append(getFieldName(sort.getName())).append(" ").append(sort.getType());
            if (i < sorts.size() - 1) {
                sqlBuilder.append(", ");
            }
        }
        return sqlBuilder.toString();
    }

    private static String getFieldName(String name) {

        String columnName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
        if (!SqlCheckUtil.isValidSqlColumnName(columnName)) {
            throw new RuntimeException("条件参数非法");
        }
        return columnName;
    }

    private static String getConditionSql(Condition condition) {
        ConditionExtInfo extInfo = condition.getExtInfo();
        ConditionOperateEnum operate = extInfo == null ? ConditionOperateEnum.EQUAL : ConditionOperateEnum.getOperate(extInfo.getOperate());
        String valueType = extInfo != null && StringUtils.equalsAny(extInfo.getValueType(), ConditionExtInfo.VALUE_TYPE_NUMBER, ConditionExtInfo.VALUE_TYPE_TEXT) ? extInfo.getValueType() : ConditionExtInfo.VALUE_TYPE_TEXT;
        String conditionSql = getFieldName(condition.getName()) + " ";
        if (StringUtils.isNotEmpty(operate.fmt)) {
            return conditionSql + String.format(operate.fmt, getFmtSqlValue(condition.getValue(), valueType, operate));
        }
        return conditionSql + operate.operate + " " + autoStr(condition.getValue(), valueType);
    }

    private static Object[] getFmtSqlValue(String value, String valueType, ConditionOperateEnum operate) {
        if (Objects.equals(valueType, ConditionExtInfo.VALUE_TYPE_TEXT)) {
            switch (operate) {
                case LIKE:
                    return new String[]{"'%" + value + "%'"};
                case LEFT_LIKE:
                    return new String[]{"'" + value + "%'"};
                case RIGHT_LIKE:
                    return new String[]{"'%" + value + "'"};
                case NOT_IN:
                case IN:
                    return new String[]{Joiner.on(",").join(Arrays.stream(value.split(",")).map(v -> autoStr(v, valueType)).toArray())};
                case BETWEEN_AND:
                    return Arrays.stream(value.split(",")).map(v -> autoStr(v, valueType)).toArray();

            }
        }
        return new String[]{autoStr(value, valueType)};
    }

    private static String autoStr(String value, String valueType) {
        if (Objects.equals(valueType, ConditionExtInfo.VALUE_TYPE_TEXT)) {
            return "'" + value + "'";
        }
        return value;
    }


}
