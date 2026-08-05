package com.dqc.compare;

import com.dqc.compare.dto.CompareReport;
import com.dqc.compare.dto.SourceHealth;
import com.dqc.compare.entity.CompareTaskConfig;
import com.dqc.compare.model.SourceType;
import com.dqc.compare.service.ComparePipeline;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试：样例数据全流程（解析 -> 映射 -> 规则 -> 结果/工单）与新增规则验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ComparePipelineIntegrationTest {

    @Autowired
    private ComparePipeline pipeline;

    @TempDir
    Path tempDir;

    @Test
    void sampleData_runMatchesAcceptanceSignature() throws Exception {
        Path input = tempDir.resolve("input");
        copyRecursively(Path.of("data/input"), input);

        CompareTaskConfig config = new CompareTaskConfig();
        config.setTaskName("e2e-sample");
        config.setEnabled(true);
        config.setCronExpression("0 0 2 * * ?");
        config.setProdDdlPath(input.resolve("production/latest").toString());
        config.setDdmPath(input.resolve("ddm").toString());
        config.setSoaPath(input.resolve("soa").toString());
        config.setFileSpecPath(input.resolve("file_spec").toString());
        config.setRecipients("");

        CompareReport report = pipeline.run(config);

        assertEquals("SUCCESS", report.getStatus());
        assertEquals(6, report.getTotalCount());
        assertEquals(2, report.getCriticalCount());
        assertEquals(4, report.getWarningCount());
        assertEquals(2, report.getTicketCount());

        // 源健康度：修复 Swagger2 后 SOA 侧应解析出 3 个接口模型且无失败文件
        SourceHealth soa = report.getSourceHealth().stream()
                .filter(h -> h.getSourceType() == SourceType.SOA_API)
                .findFirst().orElseThrow();
        assertEquals(3, soa.getEntityCount());
        assertEquals(0, soa.getFailedFileCount());
        assertTrue(soa.isHealthy());

        // 关键命中与字段归因（中文字段名精确指向来源侧）
        assertTrue(report.getResults().stream().anyMatch(r ->
                "中文字符检测".equals(r.getRuleName()) && "客户名".equals(r.getFieldName())));
        assertTrue(report.getResults().stream().anyMatch(r ->
                "增量文件缺少时间戳".equals(r.getRuleName())));
        assertTrue(report.getResults().stream().anyMatch(r ->
                "模型缺失生产字段".equals(r.getRuleName()) && "ACCOUNT".equals(r.getTableName())));
    }

    @Test
    void deliveryMismatchRuleFires_whenSoaAndSpecDisagree() throws Exception {
        Path soaDir = Files.createDirectories(tempDir.resolve("soa"));
        Files.writeString(soaDir.resolve("order_info.yaml"), """
                swagger: "2.0"
                info: { title: Order API, version: "1.0" }
                paths: {}
                definitions:
                  ORDER_INFO:
                    type: object
                    x-deliveryType: FULL
                    properties:
                      ID: { type: integer }
                """);

        Path specDir = Files.createDirectories(tempDir.resolve("spec"));
        Path xlsx = specDir.resolve("spec.xlsx");
        try (Workbook wb = new XSSFWorkbook(); var out = Files.newOutputStream(xlsx)) {
            Sheet s = wb.createSheet("spec");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("表名");
            h.createCell(1).setCellValue("字段名");
            h.createCell(2).setCellValue("下发");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("ORDER_INFO");
            r.createCell(1).setCellValue("ID");
            r.createCell(2).setCellValue("增量");
            wb.write(out);
        }

        CompareTaskConfig config = new CompareTaskConfig();
        config.setTaskName("e2e-delivery");
        config.setEnabled(true);
        config.setSoaPath(soaDir.toString());
        config.setFileSpecPath(specDir.toString());
        config.setRecipients("");

        CompareReport report = pipeline.run(config);

        assertEquals("SUCCESS", report.getStatus());
        assertTrue(report.getResults().stream().anyMatch(r ->
                "下发方式不一致".equals(r.getRuleName())), "SOA FULL 与文件规范增量应命中下发方式不一致");
    }

    private static void copyRecursively(Path src, Path dst) throws Exception {
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(p, target);
                }
            }
        }
    }
}
