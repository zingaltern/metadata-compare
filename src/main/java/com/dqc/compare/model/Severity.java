package com.dqc.compare.model;

/**
 * 结果严重级别（对应文档 3.2 结果分级）。
 */
public enum Severity {
    CRITICAL,
    WARNING,
    INFO;

    public static Severity from(String s) {
        if (s == null) {
            return INFO;
        }
        try {
            return Severity.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
