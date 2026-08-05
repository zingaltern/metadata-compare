package com.dqc.compare.rule.functions;

import com.ql.util.express.Operator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义函数 regexMatch(s, regex)：正则匹配。
 * 兼容规则中写成的「反斜杠+u+4位十六进制」形式（无论 QLExpress 是否预先转义都正确处理）。
 */
public class RegexMatchFunction extends Operator {

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\" + "u([0-9a-fA-F]{4})");

    @Override
    public Object executeInner(Object[] args) {
        if (args == null || args.length < 2 || args[0] == null || args[1] == null) {
            return false;
        }
        String s = args[0].toString();
        String regex = normalizeUnicodeEscapes(args[1].toString());
        try {
            return Pattern.compile(regex).matcher(s).find();
        } catch (Exception e) {
            return false;
        }
    }

    static String normalizeUnicodeEscapes(String in) {
        if (in == null) {
            return null;
        }
        Matcher m = UNICODE_ESCAPE.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, String.valueOf((char) Integer.parseInt(m.group(1), 16)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
