package com.dqc.compare.rule.functions;

import com.ql.util.express.Operator;

/**
 * 自定义函数 hasChinese(s)：判断字符串是否包含中文字符（CJK 统一表意文字）。
 *
 * <p>为什么不用 regexMatch(..., '[\u4e00-\u9fa5]')：QLExpress 的词法分析器会吞掉
 * 字符串字面量中的反斜杠，导致 '[\u4e00-\u9fa5]' 实际变成 '[u4e00-u9fa5]'，而 Java 正则
 * 会把其中的 '0-u' 解释为一个码点区间（0x30~0x75），进而匹配所有数字与 Latin 字母，
 * 造成严重误报。这里改用码点判断，彻底规避转义问题。</p>
 */
public class HasChineseFunction extends Operator {

    @Override
    public Object executeInner(Object[] args) {
        if (args == null || args.length < 1 || args[0] == null) {
            return false;
        }
        String s = args[0].toString();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (isCjk(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isCjk(int cp) {
        // CJK 统一表意文字：基础平面 + 扩展 A/B/C/D/E/F/G/H
        return (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x20000 && cp <= 0x2A6DF)
                || (cp >= 0x2A700 && cp <= 0x2B73F)
                || (cp >= 0x2B740 && cp <= 0x2B81F)
                || (cp >= 0x2B820 && cp <= 0x2CEAF)
                || (cp >= 0x2CEB0 && cp <= 0x2EBEF);
    }
}
