package com.dqc.compare;

import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.ParseRequest;
import com.dqc.compare.parser.impl.ExcelSpecParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件规范解析器测试：中英文表头、下发类型优先级、多 sheet。
 */
class ExcelSpecParserTest {

    @TempDir
    Path dir;

    @Test
    void parsesChineseHeadersAndDeliveryTypePrecedence() throws Exception {
        Path file = dir.resolve("spec.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("spec");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("表名");
            h.createCell(1).setCellValue("字段名");
            h.createCell(2).setCellValue("下发类型"); // 含「下发」应判为 deliveryType 而非 dataType
            h.createCell(3).setCellValue("类型");
            h.createCell(4).setCellValue("可空");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("AUDIT_LOG");
            r.createCell(1).setCellValue("CREATE_TIME");
            r.createCell(2).setCellValue("增量");
            r.createCell(3).setCellValue("DATETIME");
            r.createCell(4).setCellValue("否");
            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }

        List<StandardMetadata> list = new ExcelSpecParser().parse(new ParseRequest(file));
        assertEquals(1, list.size());
        StandardMetadata meta = list.get(0);
        assertEquals("AUDIT_LOG", meta.getTableName());
        assertEquals("INCREMENTAL", meta.getDeliveryType());
        assertEquals("DATETIME", meta.findField("CREATE_TIME").getDataType());
        assertFalse(meta.findField("CREATE_TIME").getNullable());
    }

    @Test
    void parsesEnglishHeadersAndMultipleSheets() throws Exception {
        Path file = dir.resolve("spec-en.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s1 = wb.createSheet("first");
            Row h1 = s1.createRow(0);
            h1.createCell(0).setCellValue("table_name");
            h1.createCell(1).setCellValue("field_name");
            h1.createCell(2).setCellValue("delivery_type");
            h1.createCell(3).setCellValue("data_type");
            Row r1 = s1.createRow(1);
            r1.createCell(0).setCellValue("CONFIG");
            r1.createCell(1).setCellValue("UPDATE_TIME");
            r1.createCell(2).setCellValue("FULL");
            r1.createCell(3).setCellValue("DATETIME");

            Sheet s2 = wb.createSheet("second");
            Row h2 = s2.createRow(0);
            h2.createCell(0).setCellValue("Table");
            h2.createCell(1).setCellValue("Field");
            h2.createCell(2).setCellValue("Delivery");
            Row r2 = s2.createRow(1);
            r2.createCell(0).setCellValue("CACHE");
            r2.createCell(1).setCellValue("KEY");
            r2.createCell(2).setCellValue("Full");
            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }

        List<StandardMetadata> list = new ExcelSpecParser().parse(new ParseRequest(file));
        assertEquals(2, list.size());
        StandardMetadata config = list.stream().filter(m -> "CONFIG".equals(m.getTableName())).findFirst().orElseThrow();
        assertEquals("FULL", config.getDeliveryType());
        assertEquals("DATETIME", config.findField("UPDATE_TIME").getDataType());
        StandardMetadata cache = list.stream().filter(m -> "CACHE".equals(m.getTableName())).findFirst().orElseThrow();
        assertEquals("FULL", cache.getDeliveryType());
        assertEquals("KEY", cache.findField("KEY").getFieldName());
    }
}
