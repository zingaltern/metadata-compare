package com.dqc.compare.parser.impl;

import com.dqc.compare.model.FieldMeta;
import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.MetadataParser;
import com.dqc.compare.parser.ParseRequest;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.*;

/**
 * 文件规范解析器（.xlsx / .xls），基于 Apache POI。
 * 文件规范为自设计模板（见 README）：首行为表头，按表名分组。
 */
@Component
public class ExcelSpecParser implements MetadataParser {

    private static final Logger log = LoggerFactory.getLogger(ExcelSpecParser.class);

    @Override
    public boolean supports(ParseRequest req) {
        String n = req.getFileName().toLowerCase();
        return n.endsWith(".xlsx") || n.endsWith(".xls");
    }

    @Override
    public List<StandardMetadata> parse(ParseRequest req) throws Exception {
        List<StandardMetadata> result = new ArrayList<>();
        if (Files.size(req.getPath()) == 0) {
            return result;
        }
        try (Workbook wb = WorkbookFactory.create(req.getPath().toFile())) {
            DataFormatter df = new DataFormatter();
            int parsedSheets = 0;
            for (Sheet sheet : wb) {
                Map<String, StandardMetadata> byTable = parseSheet(sheet, df);
                if (byTable == null) {
                    continue; // 该 sheet 无表头或缺少「表名」列
                }
                parsedSheets++;
                result.addAll(byTable.values());
            }
            log.info("文件规范解析完成：{} -> {} 张表（{} 个有效 sheet）", req.getFileName(), result.size(), parsedSheets);
        } catch (Exception e) {
            log.warn("解析文件规范失败，已跳过: {} -> {}", req.getFileName(), e.getMessage());
        }
        return result;
    }

    /** 解析单个 sheet；缺表头或「表名」列时返回 null。 */
    private Map<String, StandardMetadata> parseSheet(Sheet sheet, DataFormatter df) {
        Row header = sheet.getRow(0);
        if (header == null) {
            return null;
        }
        Map<String, Integer> col = buildColumnIndex(header, df);
        Integer tableNameIdx = col.get("tableName");
        if (tableNameIdx == null) {
            return null;
        }
        Map<String, StandardMetadata> byTable = new LinkedHashMap<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String tableName = df.formatCellValue(row.getCell(tableNameIdx)).trim();
            if (tableName.isEmpty()) {
                continue;
            }
            StandardMetadata meta = byTable.computeIfAbsent(tableName,
                    k -> new StandardMetadata(SourceType.FILE_SPEC, k));
            FieldMeta fm = new FieldMeta();
            fm.setFieldName(str(col, row, df, "fieldName"));
            fm.setDataType(str(col, row, df, "dataType"));
            fm.setLength(intVal(col, row, df, "length"));
            fm.setPrecision(intVal(col, row, df, "precision"));
            fm.setNullable(boolVal(col, row, df, "nullable"));
            fm.setComment(str(col, row, df, "comment"));
            fm.setConstraint(str(col, row, df, "constraint"));
            meta.getFields().add(fm);
            String dt = normalizeDeliveryType(str(col, row, df, "deliveryType"));
            if (dt != null && meta.getDeliveryType() == null) {
                meta.setDeliveryType(dt);
            }
        }
        return byTable;
    }

    private Map<String, Integer> buildColumnIndex(Row header, DataFormatter df) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String h = df.formatCellValue(header.getCell(i)).trim().toLowerCase(Locale.ROOT);
            map.put(alias(h), i);
        }
        return map;
    }

    private String alias(String h) {
        String t = h.toLowerCase(Locale.ROOT).trim();
        if (h.contains("表名") || h.contains("实体") || h.contains("接口")) {
            return "tableName";
        }
        if (h.contains("字段名") || h.equals("字段")) {
            return "fieldName";
        }
        // 下发类型优先于泛化的“类型”，否则“下发类型”会因包含“类型”被误判为 dataType
        if (h.contains("下发") || h.contains("增量") || h.contains("全量") || h.contains("交付")) {
            return "deliveryType";
        }
        if (h.contains("数据类型") || h.equals("类型")) {
            return "dataType";
        }
        if (h.contains("长度")) {
            return "length";
        }
        if (h.contains("精度")) {
            return "precision";
        }
        if (h.contains("可空") || h.contains("是否可空")) {
            return "nullable";
        }
        if (h.contains("备注") || h.contains("注释") || h.contains("说明")) {
            return "comment";
        }
        if (h.contains("约束") || h.contains("主键")) {
            return "constraint";
        }
        // 英文表头别名（table_name / field_name / data_type / delivery_type 等）
        if (t.equals("table") || t.equals("table name") || t.equals("table_name")
                || t.equals("entity") || t.equals("api") || t.equals("interface")) {
            return "tableName";
        }
        if (t.equals("field") || t.equals("field name") || t.equals("field_name")) {
            return "fieldName";
        }
        if (t.equals("delivery") || t.equals("delivery type") || t.equals("delivery_type")
                || t.equals("incremental") || t.equals("full")) {
            return "deliveryType";
        }
        if (t.equals("type") || t.equals("data type") || t.equals("data_type") || t.equals("datatype")) {
            return "dataType";
        }
        if (t.equals("length") || t.equals("len") || t.equals("size")) {
            return "length";
        }
        if (t.equals("precision") || t.equals("scale")) {
            return "precision";
        }
        if (t.equals("nullable") || t.equals("not null")) {
            return "nullable";
        }
        if (t.equals("comment") || t.equals("remark") || t.equals("description") || t.equals("desc")) {
            return "comment";
        }
        if (t.equals("constraint") || t.equals("pk") || t.equals("primary key")) {
            return "constraint";
        }
        return h;
    }

    private String str(Map<String, Integer> col, Row row, DataFormatter df, String key) {
        Integer i = col.get(key);
        if (i == null) {
            return null;
        }
        Cell c = row.getCell(i);
        return c == null ? null : df.formatCellValue(c).trim();
    }

    private Integer intVal(Map<String, Integer> col, Row row, DataFormatter df, String key) {
        String s = str(col, row, df, key);
        if (s == null || s.isEmpty()) {
            return null;
        }
        String d = s.replaceAll("[^0-9]", "");
        return d.isEmpty() ? null : Integer.parseInt(d);
    }

    private Boolean boolVal(Map<String, Integer> col, Row row, DataFormatter df, String key) {
        String s = str(col, row, df, key);
        if (s == null || s.isEmpty()) {
            return null;
        }
        s = s.toLowerCase(Locale.ROOT);
        if (s.equals("是") || s.equals("y") || s.equals("yes") || s.equals("true") || s.equals("1")) {
            return true;
        }
        if (s.equals("否") || s.equals("n") || s.equals("no") || s.equals("false") || s.equals("0")) {
            return false;
        }
        return null;
    }

    private String normalizeDeliveryType(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        if (u.contains("增")) {
            return "INCREMENTAL";
        }
        if (u.contains("全")) {
            return "FULL";
        }
        return u;
    }
}
