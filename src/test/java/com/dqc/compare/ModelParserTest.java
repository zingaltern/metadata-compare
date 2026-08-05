package com.dqc.compare;

import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.ParseRequest;
import com.dqc.compare.parser.impl.JsonModelParser;
import com.dqc.compare.parser.impl.XmlModelParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DDM 模型解析器测试：XML 属性式、JSON entities/models 根。
 */
class ModelParserTest {

    @TempDir
    Path dir;

    @Test
    void parsesDdmXml() throws Exception {
        XmlModelParser parser = new XmlModelParser();
        List<StandardMetadata> list = parser.parse(new ParseRequest(Path.of("data/input/ddm/customer_model.xml")));
        StandardMetadata meta = list.stream()
                .filter(m -> "CUSTOMER".equals(m.getTableName()))
                .findFirst().orElseThrow();
        assertEquals(SourceType.DDM_MODEL, meta.getSourceType());
        assertEquals("CUSTOMER", meta.getTableName());
        assertNotNull(meta.findField("CUST_NAME"));
        assertEquals(100, meta.findField("CUST_NAME").getLength());
    }

    @Test
    void parsesJsonEntitiesRoot() throws Exception {
        JsonModelParser parser = new JsonModelParser();
        List<StandardMetadata> list = parser.parse(new ParseRequest(Path.of("data/input/ddm/product_model.json")));
        assertEquals(1, list.size());
        assertEquals("PRODUCT", list.get(0).getTableName());
    }

    @Test
    void parsesJsonModelsRoot() throws Exception {
        Path file = dir.resolve("models.json");
        Files.writeString(file, """
                {
                  "models": [
                    { "name": "USER", "fields": [ { "name": "UID", "type": "BIGINT", "nullable": false } ] }
                  ]
                }
                """);
        List<StandardMetadata> list = new JsonModelParser().parse(new ParseRequest(file));
        assertEquals(1, list.size());
        assertEquals("USER", list.get(0).getTableName());
        assertNotNull(list.get(0).findField("UID"));
    }
}
