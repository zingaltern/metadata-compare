package com.dqc.compare.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条字段级映射：把多个来源里"同一字段的不同名字"归一到 logicalName。
 * 例如 生产 客户名 / DDM CUST_NAME / SOA custName 都归一到逻辑字段名 CUST_NAME。
 * 需置于所属表映射（MappingEntry.fields）之下，仅对对应逻辑表生效。
 */
public class FieldMappingEntry {
    private String logicalName;
    private List<String> aliases = new ArrayList<>();

    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
    public List<String> getAliases() { return aliases; }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }
}
