package com.dqc.compare.rule.functions;

import com.ql.util.express.Operator;

/**
 * 自定义函数 deliveryMismatch(a, b)：判断 SOA 与文件规范两侧的下发方式声明是否不一致。
 *
 * <p>归一化规则：去除空白、转大写；包含「增」→ INCREMENTAL，包含「全」→ FULL。
 * 任一侧为空则无法判定，返回 false（不告警）；两侧均非空且归一化后不同才返回 true。</p>
 */
public class DeliveryMismatchFunction extends Operator {

    @Override
    public Object executeInner(Object[] args) {
        if (args == null || args.length < 2) {
            return false;
        }
        String a = normalize(args[0]);
        String b = normalize(args[1]);
        if (a == null || b == null) {
            return false;
        }
        return !a.equals(b);
    }

    private static String normalize(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim().toUpperCase();
        if (s.isEmpty()) {
            return null;
        }
        if (s.contains("增")) {
            return "INCREMENTAL";
        }
        if (s.contains("全")) {
            return "FULL";
        }
        return s;
    }
}
