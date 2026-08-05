package com.dqc.compare.model;

/**
 * 结果分类（对应文档 5.2 compare_result.category）。
 */
public enum Category {
    VIOLATION,  // 违规（中文、下发方式等硬约束）
    STRUCTURE,  // 结构差异（长度/精度/类型/字段缺失）
    LOGIC;      // 业务逻辑

    public static Category from(String s) {
        if (s == null) {
            return VIOLATION;
        }
        try {
            return Category.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return VIOLATION;
        }
    }
}
