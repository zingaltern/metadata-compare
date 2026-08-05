package com.dqc.compare;

import com.dqc.compare.config.AppProperties;
import com.dqc.compare.config.TableMappingLoader;
import com.dqc.compare.model.FieldMeta;
import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.ParseRequest;
import com.dqc.compare.parser.impl.MySqlDdlParser;
import com.dqc.compare.rule.RuleDef;
import com.dqc.compare.rule.RuleLoader;
import com.dqc.compare.rule.functions.HasChineseFunction;
import com.dqc.compare.rule.functions.DeliveryMismatchFunction;
import com.dqc.compare.rule.functions.TypeCompatibleFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键纯逻辑单元测试（对应审计 T9：补齐自动化测试，保障回归）。
 */
class CoreLogicTest {

    @Test
    void typeCompatible_numericAndStringGroups() {
        var f = new TypeCompatibleFunction();
        // 数值组内部兼容
        assertTrue((Boolean) f.executeInner(new Object[]{"INT", "BIGINT"}));
        assertTrue((Boolean) f.executeInner(new Object[]{"DECIMAL", "DOUBLE"}));
        // 字符串组内部兼容
        assertTrue((Boolean) f.executeInner(new Object[]{"VARCHAR", "TEXT"}));
        // 跨组不兼容
        assertFalse((Boolean) f.executeInner(new Object[]{"INT", "VARCHAR"}));
        // 空参安全
        assertFalse((Boolean) f.executeInner(new Object[]{"INT", null}));
    }

    @Test
    void hasChinese_detectsCJK() {
        var f = new HasChineseFunction();
        assertTrue((Boolean) f.executeInner(new Object[]{"客户名称"}));
        assertFalse((Boolean) f.executeInner(new Object[]{"cust_name"}));
        assertFalse((Boolean) f.executeInner(new Object[]{null}));
    }

    @Test
    void deliveryMismatch_normalizesAndCompares() {
        var f = new DeliveryMismatchFunction();
        // 归一化后相同 -> 不告警
        assertFalse((Boolean) f.executeInner(new Object[]{"FULL", "全量"}));
        assertFalse((Boolean) f.executeInner(new Object[]{"INCREMENTAL", "增量"}));
        // 不同 -> 告警
        assertTrue((Boolean) f.executeInner(new Object[]{"FULL", "增量"}));
        assertTrue((Boolean) f.executeInner(new Object[]{"全量", "INCREMENTAL"}));
        // 任一侧为空 -> 不告警
        assertFalse((Boolean) f.executeInner(new Object[]{"FULL", null}));
        assertFalse((Boolean) f.executeInner(new Object[]{null, "增量"}));
    }

    @Test
    void mySqlDdlParser_parsesFields(@TempDir Path dir) throws Exception {
        String ddl = "CREATE TABLE `T_ACCT` (\n"
                + "  `id` BIGINT NOT NULL COMMENT '主键',\n"
                + "  `cust_name` VARCHAR(64) NOT NULL COMMENT '客户名称',\n"
                + "  `age` INT NULL,\n"
                + "  PRIMARY KEY (`id`, `cust_name`)\n"
                + ");";
        Path sql = dir.resolve("t_acct.sql");
        Files.writeString(sql, ddl);

        MySqlDdlParser parser = new MySqlDdlParser();
        List<StandardMetadata> list = parser.parse(new ParseRequest(sql));
        assertEquals(1, list.size());
        StandardMetadata meta = list.get(0);
        assertEquals("T_ACCT", meta.getTableName());
        assertEquals(SourceType.PRODUCTION_DDL, meta.getSourceType());
        assertEquals(3, meta.getFields().size());

        FieldMeta cust = meta.getFields().stream()
                .filter(f -> "cust_name".equals(f.getFieldName())).findFirst().orElseThrow();
        assertEquals("VARCHAR", cust.getDataType());
        assertEquals(64, cust.getLength());
        assertFalse(cust.getNullable()); // NOT NULL -> 不可空
        assertEquals("客户名称", cust.getComment());
        assertEquals("PK", cust.getConstraint()); // 表级复合主键补齐

        FieldMeta age = meta.getFields().stream()
                .filter(f -> "age".equals(f.getFieldName())).findFirst().orElseThrow();
        assertTrue(age.getNullable()); // 无 NOT -> 可空
        assertNull(age.getConstraint());
    }

    @Test
    void ruleLoader_skipsInvalidRules(@TempDir Path dir) throws Exception {
        String yaml = "rules:\n"
                + "  - name: 合法规则\n"
                + "    severity: CRITICAL\n"
                + "    category: VIOLATION\n"
                + "    action: CREATE_REVIEW_TICKET\n"
                + "    condition: \"field != null\"\n"
                + "  - severity: WARNING\n"          // 缺 name
                + "    category: STRUCTURE\n"
                + "    action: REPORT_ONLY\n"
                + "    condition: \"tableName != null\"\n"
                + "  - name: 缺condition\n"           // 缺 condition
                + "    severity: INFO\n"
                + "    category: LOGIC\n"
                + "    action: REPORT_ONLY\n";
        Path file = dir.resolve("compare-rules.yml");
        Files.writeString(file, yaml);

        AppProperties props = new AppProperties();
        props.getRules().setPath(file.toString());
        RuleLoader loader = new RuleLoader(props);
        loader.scheduledReload();

        List<RuleDef> rules = loader.getRules();
        assertEquals(1, rules.size());
        assertEquals("合法规则", rules.get(0).getName());
    }

    @Test
    void tableMappingLoader_resolvesLogicalNameAndField(@TempDir Path dir) throws Exception {
        String yaml = "mappings:\n"
                + "  - logicalName: CUSTOMER\n"
                + "    aliases: [T_ACCT, ACCT_MODEL, ACCT_INFO]\n"
                + "    fields:\n"
                + "      - logicalName: CUST_NAME\n"
                + "        aliases: [客户名, custName]\n";
        Path file = dir.resolve("table-mappings.yml");
        Files.writeString(file, yaml);

        AppProperties props = new AppProperties();
        props.getTableMapping().setPath(file.toString());
        TableMappingLoader loader = new TableMappingLoader(props);
        loader.scheduledReload();

        assertEquals("CUSTOMER", loader.resolveLogicalName("T_ACCT"));
        assertEquals("CUSTOMER", loader.resolveLogicalName("ACCT_INFO"));
        assertEquals("T_ACCT_RAW", loader.resolveLogicalName("T_ACCT_RAW")); // 未映射原样返回
        assertEquals("CUST_NAME", loader.resolveFieldName("CUSTOMER", "客户名"));
        assertEquals("CUST_NAME", loader.resolveFieldName("CUSTOMER", "custName"));
        assertNull(loader.resolveFieldName("CUSTOMER", "unknown_field"));
    }
}
