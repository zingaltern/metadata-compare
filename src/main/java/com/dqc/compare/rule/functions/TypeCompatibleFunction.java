package com.dqc.compare.rule.functions;

import com.ql.util.express.Operator;

import java.util.Set;

/**
 * 自定义函数 typeCompatible(a, b)：类型兼容性（归一化后比较）。
 * 数值类型(INT/DECIMAL)之间、字符串类型之间视为兼容。
 */
public class TypeCompatibleFunction extends Operator {

    private static final Set<String> NUMERIC = Set.of("INT", "DECIMAL");
    private static final Set<String> STRINGY = Set.of("STRING");

    @Override
    public Object executeInner(Object[] args) {
        if (args == null || args.length < 2 || args[0] == null || args[1] == null) {
            return false;
        }
        String g1 = group(args[0].toString());
        String g2 = group(args[1].toString());
        if (g1.equals(g2)) {
            return true;
        }
        if (NUMERIC.contains(g1) && NUMERIC.contains(g2)) {
            return true;
        }
        return STRINGY.contains(g1) && STRINGY.contains(g2);
    }

    private static String group(String type) {
        if (type == null) {
            return "OTHER";
        }
        String t = type.toUpperCase().replaceAll("\\s+", "");
        // 形如 integer:int64 / number:double 取主类型
        int colon = t.indexOf(':');
        if (colon >= 0) {
            t = t.substring(0, colon);
        }
        return switch (t) {
            case "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT" -> "INT";
            case "DECIMAL", "NUMERIC", "NUMBER", "FLOAT", "DOUBLE", "REAL" -> "DECIMAL";
            case "VARCHAR", "CHAR", "TEXT", "STRING", "CLOB", "LONGVARCHAR" -> "STRING";
            case "DATE", "DATETIME", "TIMESTAMP", "TIME" -> "DATETIME";
            case "BOOLEAN", "BOOL" -> "BOOL";
            case "JSON" -> "JSON";
            case "BINARY", "VARBINARY", "BLOB" -> "BINARY";
            default -> t;
        };
    }
}
