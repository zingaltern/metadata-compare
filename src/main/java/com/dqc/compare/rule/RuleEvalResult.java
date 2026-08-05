package com.dqc.compare.rule;

import java.util.Map;

/**
 * 单条规则的执行结果（用于结果落库与规则归因分析 trace_info）。
 */
public class RuleEvalResult {

    private final boolean matched;
    private final String error;
    private final long elapsedMs;
    /** 评估时的变量快照，用于归因分析（哪些字段、什么取值触发了规则） */
    private final Map<String, Object> variables;

    public RuleEvalResult(boolean matched, String error, long elapsedMs, Map<String, Object> variables) {
        this.matched = matched;
        this.error = error;
        this.elapsedMs = elapsedMs;
        this.variables = variables;
    }

    public boolean isMatched() { return matched; }
    public String getError() { return error; }
    public long getElapsedMs() { return elapsedMs; }
    public Map<String, Object> getVariables() { return variables; }
}
