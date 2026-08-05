package com.dqc.compare.rule;

import com.dqc.compare.model.Category;
import com.dqc.compare.model.RuleAction;
import com.dqc.compare.model.Severity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单条规则定义（来自 rules/compare-rules.yml）。
 */
public class RuleDef {

    private String name;
    private Severity severity;
    private Category category;
    private RuleAction action;
    /** 作用域：FIELD（逐字段） / TABLE（整表）。为空时由流水线按 condition 嗅探兜底。 */
    private String scope;
    /** 消息模板，支持 ${ruleName}/${tableName}/${fieldName}/${prodLength} 等占位符；为空时用通用文案。 */
    private String message;
    /** 为 true 时结果中的 fieldName 取"来源侧真实字段名"（如中文字段名），否则用逻辑字段名。 */
    private boolean fieldNameFromSource;
    private String condition;

    public RuleDef() {
    }

    public RuleDef(String name, Severity severity, Category category, RuleAction action, String condition) {
        this.name = name;
        this.severity = severity;
        this.category = category;
        this.action = action;
        this.condition = condition;
    }

    @JsonCreator
    public RuleDef(@JsonProperty("name") String name,
                   @JsonProperty("severity") String severity,
                   @JsonProperty("category") String category,
                   @JsonProperty("action") String action,
                   @JsonProperty("scope") String scope,
                   @JsonProperty("message") String message,
                   @JsonProperty("fieldNameFromSource") Boolean fieldNameFromSource,
                   @JsonProperty("condition") String condition) {
        this.name = name;
        this.severity = Severity.from(severity);
        this.category = Category.from(category);
        this.action = RuleAction.from(action);
        this.scope = scope;
        this.message = message;
        this.fieldNameFromSource = Boolean.TRUE.equals(fieldNameFromSource);
        this.condition = condition;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public RuleAction getAction() { return action; }
    public void setAction(RuleAction action) { this.action = action; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isFieldNameFromSource() { return fieldNameFromSource; }
    public void setFieldNameFromSource(boolean fieldNameFromSource) { this.fieldNameFromSource = fieldNameFromSource; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}
