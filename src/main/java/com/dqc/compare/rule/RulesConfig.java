package com.dqc.compare.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * rules/compare-rules.yml 的根结构。
 */
public class RulesConfig {

    private List<RuleDef> rules = new ArrayList<>();

    public List<RuleDef> getRules() { return rules; }
    public void setRules(List<RuleDef> rules) { this.rules = rules; }
}
