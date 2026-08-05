package com.dqc.compare;

import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.ParseRequest;
import com.dqc.compare.parser.impl.SwaggerApiParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SOA 解析器测试：OpenAPI 3 JSON 与 Swagger 2 YAML（经 V2 转换）。
 */
class SwaggerApiParserTest {

    @Test
    void parsesOpenApi3Json() throws Exception {
        SwaggerApiParser parser = new SwaggerApiParser();
        Path file = Path.of("data/input/soa/order_api.json");
        List<StandardMetadata> list = parser.parse(new ParseRequest(file));
        assertEquals(1, list.size());
        StandardMetadata order = list.get(0);
        assertEquals("Order", order.getTableName());
        assertEquals(SourceType.SOA_API, order.getSourceType());
        assertEquals("FULL", order.getDeliveryType());
        assertEquals(4, order.getFields().size());
        assertNotNull(order.findField("orderAmt"));
    }

    @Test
    void parsesSwagger2YamlViaConverter() throws Exception {
        SwaggerApiParser parser = new SwaggerApiParser();
        for (String name : List.of("account_api.yaml", "customer_api.yaml")) {
            Path file = Path.of("data/input/soa", name);
            List<StandardMetadata> list = parser.parse(new ParseRequest(file));
            assertEquals(1, list.size(), "Swagger2 文件应被解析: " + name);
            StandardMetadata meta = list.get(0);
            assertEquals(SourceType.SOA_API, meta.getSourceType());
            assertEquals("FULL", meta.getDeliveryType(), "x-deliveryType 应被保留: " + name);
            assertFalse(meta.getFields().isEmpty());
        }
    }
}
