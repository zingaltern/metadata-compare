package com.dqc.compare.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一中间格式（对应文档 4.2）。
 * 所有数据源（生产 DDL / DDM 模型 / SOA 接口 / 文件规范）解析后统一转换为该结构。
 */
public class StandardMetadata implements Serializable {

    /** 来源类型 */
    private SourceType sourceType;
    /** 表名 / 实体名 / 接口名 */
    private String tableName;
    /** 字段列表 */
    private List<FieldMeta> fields = new ArrayList<>();
    /** 扩展属性（如 deliveryType 下发方式） */
    private Map<String, Object> extra = new HashMap<>();

    public StandardMetadata() {
    }

    public StandardMetadata(SourceType sourceType, String tableName) {
        this.sourceType = sourceType;
        this.tableName = tableName;
    }

    private Map<String, Object> ensureExtra() {
        if (extra == null) {
            extra = new HashMap<>();
        }
        return extra;
    }

    /** 下发方式（增量/全量）—— 便捷访问，底层存于 extra */
    public String getDeliveryType() {
        return extra == null ? null : (String) extra.get("deliveryType");
    }

    public void setDeliveryType(String deliveryType) {
        ensureExtra().put("deliveryType", deliveryType);
    }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public List<FieldMeta> getFields() { return fields; }
    public void setFields(List<FieldMeta> fields) { this.fields = fields; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }

    /** 按字段名查找（忽略大小写） */
    public FieldMeta findField(String name) {
        if (name == null || fields == null) {
            return null;
        }
        for (FieldMeta f : fields) {
            if (name.equalsIgnoreCase(f.getFieldName())) {
                return f;
            }
        }
        return null;
    }
}
