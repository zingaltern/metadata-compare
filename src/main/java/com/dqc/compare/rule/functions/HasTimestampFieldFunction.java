package com.dqc.compare.rule.functions;

import com.dqc.compare.model.FieldMeta;
import com.ql.util.express.Operator;

import java.util.List;
import java.util.Map;

/**
 * 自定义函数 hasTimestampField(fields)：列表是否含时间戳字段。
 */
public class HasTimestampFieldFunction extends Operator {

    @Override
    public Object executeInner(Object[] args) {
        if (args == null || args.length < 1 || args[0] == null) {
            return false;
        }
        Object o = args[0];
        List<?> fields;
        if (o instanceof List) {
            fields = (List<?>) o;
        } else {
            return false;
        }
        for (Object f : fields) {
            String name = fieldName(f);
            if (name != null && isTimestamp(name)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static String fieldName(Object f) {
        if (f instanceof FieldMeta fm) {
            return fm.getFieldName();
        }
        if (f instanceof Map) {
            Object v = ((Map<String, Object>) f).get("fieldName");
            return v == null ? null : v.toString();
        }
        return null;
    }

    private static boolean isTimestamp(String name) {
        String n = name.toLowerCase();
        return n.contains("time") || n.contains("date") || n.contains("ts")
                || n.contains("时间戳") || n.contains("gmt")
                || n.contains("_dt") || n.contains("ctime") || n.contains("utime")
                || n.contains("mtime");
    }
}
