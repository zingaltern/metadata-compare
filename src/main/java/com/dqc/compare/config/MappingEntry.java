package com.dqc.compare.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条表名映射：把多个来源里"同一张表的不同名字"归一到 logicalName。
 * 例如 生产 T_CUST / DDM CUSTOMER / SOA CUST_INFO 都归一到逻辑表名 CUSTOMER。
 */
public class MappingEntry {
    private String logicalName;
    private List<String> aliases = new ArrayList<>();
    /** 字段级映射：同一张表内不同来源的同义字段归一到逻辑字段名 */
    private List<FieldMappingEntry> fields = new ArrayList<>();

    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
    public List<String> getAliases() { return aliases; }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }
    public List<FieldMappingEntry> getFields() { return fields; }
    public void setFields(List<FieldMappingEntry> fields) { this.fields = fields; }
}
