package com.dqc.compare.model;

/**
 * 规则触发后的行为（对应文档 4.3 action 字段）。
 */
public enum RuleAction {
    REPORT_ONLY,            // 仅记录
    CREATE_REVIEW_TICKET;   // 创建复核工单

    public static RuleAction from(String s) {
        if (s == null) {
            return REPORT_ONLY;
        }
        try {
            return RuleAction.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return REPORT_ONLY;
        }
    }
}
