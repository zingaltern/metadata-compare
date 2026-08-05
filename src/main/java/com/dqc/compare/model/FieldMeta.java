package com.dqc.compare.model;

import java.io.Serializable;

/**
 * 统一中间格式中的字段元数据（对应文档 4.2 fields[]）。
 */
public class FieldMeta implements Serializable {

    /** 字段名 */
    private String fieldName;
    /** 数据类型（如 BIGINT / VARCHAR / DECIMAL） */
    private String dataType;
    /** 长度 */
    private Integer length;
    /** 精度 */
    private Integer precision;
    /** 是否可空 */
    private Boolean nullable;
    /** 注释 / 含义 */
    private String comment;
    /** 约束（如 PK / FK / UK） */
    private String constraint;

    public FieldMeta() {
    }

    public FieldMeta(String fieldName, String dataType) {
        this.fieldName = fieldName;
        this.dataType = dataType;
    }

    // ---- getters / setters ----
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getLength() { return length; }
    public void setLength(Integer length) { this.length = length; }
    public Integer getPrecision() { return precision; }
    public void setPrecision(Integer precision) { this.precision = precision; }
    public Boolean getNullable() { return nullable; }
    public void setNullable(Boolean nullable) { this.nullable = nullable; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getConstraint() { return constraint; }
    public void setConstraint(String constraint) { this.constraint = constraint; }

    @Override
    public String toString() {
        return "FieldMeta{" +
                "fieldName='" + fieldName + '\'' +
                ", dataType='" + dataType + '\'' +
                ", length=" + length +
                ", precision=" + precision +
                ", nullable=" + nullable +
                ", comment='" + comment + '\'' +
                ", constraint='" + constraint + '\'' +
                '}';
    }
}
