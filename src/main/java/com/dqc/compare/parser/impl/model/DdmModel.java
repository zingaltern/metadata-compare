package com.dqc.compare.parser.impl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.dqc.compare.model.FieldMeta;
import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * DDM 模型解析 POJO（XML 与 JSON 两种格式共享映射逻辑）。
 * DDM 模型为内部格式，结构由本系统自设计（见 README 文件规范章节）。
 */
public final class DdmModel {

    private DdmModel() {
    }

    // ---------------- XML 变体（JAXB/DOM 风格，使用 Jackson XML） ----------------
    @JacksonXmlRootElement(localName = "ddm")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DdmXmlRoot {
        @JacksonXmlProperty(localName = "entity")
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<DdmXmlEntity> entities = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DdmXmlEntity {
        @JacksonXmlProperty(isAttribute = true, localName = "name")
        public String name;
        @JacksonXmlProperty(localName = "field")
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<DdmXmlField> fields = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DdmXmlField {
        @JacksonXmlProperty(isAttribute = true, localName = "name")
        public String name;
        @JacksonXmlProperty(isAttribute = true, localName = "type")
        public String type;
        @JacksonXmlProperty(isAttribute = true, localName = "nullable")
        public Boolean nullable;
        @JacksonXmlProperty(isAttribute = true, localName = "comment")
        public String comment;
        @JacksonXmlProperty(isAttribute = true, localName = "length")
        public String length;
        @JacksonXmlProperty(isAttribute = true, localName = "precision")
        public String precision;
        @JacksonXmlProperty(isAttribute = true, localName = "constraint")
        public String constraint;
    }

    // ---------------- JSON 变体 ----------------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DdmJsonRoot {
        public List<DdmJsonEntity> entities = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DdmJsonEntity {
        public String name;
        public List<DdmJsonField> fields = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DdmJsonField {
        public String name;
        public String type;
        public Boolean nullable;
        public String comment;
        public Integer length;
        public Integer precision;
        public String constraint;
    }

    // ---------------- 映射为统一中间格式 ----------------
    public static StandardMetadata toStandard(DdmXmlEntity e, SourceType sourceType) {
        StandardMetadata meta = new StandardMetadata(sourceType, e.name);
        for (DdmXmlField f : e.fields) {
            meta.getFields().add(mapField(f.name, f.type, f.nullable, f.comment, toInt(f.length), toInt(f.precision), f.constraint));
        }
        return meta;
    }

    public static StandardMetadata toStandard(DdmJsonEntity e, SourceType sourceType) {
        StandardMetadata meta = new StandardMetadata(sourceType, e.name);
        for (DdmJsonField f : e.fields) {
            meta.getFields().add(mapField(f.name, f.type, f.nullable, f.comment, f.length, f.precision, f.constraint));
        }
        return meta;
    }

    private static FieldMeta mapField(String name, String type, Boolean nullable, String comment,
                                      Integer length, Integer precision, String constraint) {
        FieldMeta fm = new FieldMeta(name, type);
        fm.setNullable(nullable);
        fm.setComment(comment);
        fm.setLength(length);
        fm.setPrecision(precision);
        fm.setConstraint(constraint);
        return fm;
    }

    private static Integer toInt(String s) {
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("[^0-9]", "");
        return d.isEmpty() ? null : Integer.parseInt(d);
    }
}
