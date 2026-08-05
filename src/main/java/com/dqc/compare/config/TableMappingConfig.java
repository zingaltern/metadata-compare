package com.dqc.compare.config;

import java.util.ArrayList;
import java.util.List;

/**
 * rules/table-mappings.yml 的根结构。
 */
public class TableMappingConfig {
    private List<MappingEntry> mappings = new ArrayList<>();

    public List<MappingEntry> getMappings() { return mappings; }
    public void setMappings(List<MappingEntry> mappings) { this.mappings = mappings; }
}
