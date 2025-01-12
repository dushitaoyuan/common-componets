package com.taoyuanx.common.search.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dushitaoyuan
 * @date 2025/1/11 19:40
 */
@SuppressWarnings("all")
public class SqlCheckUtil {
    private static final String SQL_INJECTION_PATTERN = "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|EXEC|UNION|--|;|'|\"|\\)|\\(|\\)|\\(|\\)|\\(|\\))";

    private static final Pattern PATTERN = Pattern.compile(SQL_INJECTION_PATTERN);


    private static final Set<String> SQL_KEYWORDS = new HashSet<>();

    static {
        String[] keywords = {
                "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "IS", "NULL",
                "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "ON", "GROUP", "BY", "HAVING",
                "ORDER", "LIMIT", "OFFSET", "DISTINCT", "AS", "CASE", "WHEN", "THEN", "ELSE", "END",
                "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION",
                "INSERT", "UPDATE", "DELETE", "SET", "VALUES", "TRANSACTION", "COMMIT", "ROLLBACK",
                "SAVEPOINT", "EXPLAIN", "USE", "SHOW", "DESCRIBE", "DESC", "EXISTS", "ANY", "SOME", "ALL",
                "UNION", "INTERSECT", "EXCEPT", "MINUS", "CAST", "CONVERT", "TO_CHAR", "TO_NUMBER", "TO_DATE",
                "TO_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "LOCALTIME", "LOCALTIMESTAMP",
                "SYSDATE", "SYSTIME", "SYSTIMESTAMP", "USER", "SESSION_USER", "SYSTEM_USER", "CURRENT_USER",
                "SCHEMA", "DATABASE", "TRUE", "FALSE", "UNKNOWN", "BOOLEAN", "INTEGER", "INT", "SMALLINT",
                "BIGINT", "TINYINT", "FLOAT", "REAL", "DOUBLE", "DECIMAL", "NUMERIC", "CHAR", "VARCHAR",
                "LONGVARCHAR", "DATE", "TIME", "TIMESTAMP", "BINARY", "VARBINARY", "LONGVARBINARY", "BLOB",
                "CLOB", "ARRAY", "STRUCT", "REF", "ROWID", "NCHAR", "NVARCHAR", "LONGNVARCHAR", "NCLOB",
                "SQLXML", "ROW", "OBJECT", "MAP", "REF", "XML", "JSON", "ENUM", "SET", "BIT", "VARBIT",
                "LONGVARBIT", "BOOLEAN", "CURSOR", "ROWTYPE", "RECORD", "TABLE", "ARRAY", "MULTISET", "SEQUENCE"
        };
        for (String keyword : keywords) {
            SQL_KEYWORDS.add(keyword.toLowerCase());
        }
    }

    /**
     * 校验sql参数是否非法
     *
     * @param sqlParam
     * @return
     */
    public static boolean isSQLInjection(String sqlParam) {
        if (sqlParam == null || sqlParam.isEmpty()) {
            return false;
        }

        Matcher matcher = PATTERN.matcher(sqlParam);
        return matcher.find();
    }

    /**
     * 校验入参是否符合SQL列名的格式
     *
     * @param columnName 待校验的列名
     * @return 如果符合格式返回true，否则返回false
     */
    public static boolean isValidSqlColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return false;
        }

        // 列名不能是SQL关键字
        if (SQL_KEYWORDS.contains(columnName.toLowerCase())) {
            return false;
        }

        // 列名不能以数字开头
        if (Character.isDigit(columnName.charAt(0))) {
            return false;
        }

        // 列名只能包含字母、数字和下划线
        for (char c : columnName.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }

        return true;
    }
}
