package com.dqc.compare;

import com.dqc.compare.parser.MetadataParser;
import com.dqc.compare.parser.ParseDirectoryResult;
import com.dqc.compare.parser.ParserRouter;
import com.dqc.compare.parser.impl.ExcelSpecParser;
import com.dqc.compare.parser.impl.JsonModelParser;
import com.dqc.compare.parser.impl.MySqlDdlParser;
import com.dqc.compare.parser.impl.SwaggerApiParser;
import com.dqc.compare.parser.impl.XmlModelParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析器路由回归测试：相对路径（./ 前缀）与隐藏目录过滤。
 */
class ParserRouterTest {

    @Test
    void dotPrefixedRelativePath_notFiltered() throws Exception {
        ParserRouter router = new ParserRouter(List.of(
                new MySqlDdlParser(), new SwaggerApiParser(), new JsonModelParser(),
                new XmlModelParser(), new ExcelSpecParser()));
        ParseDirectoryResult r = router.parseDirectoryDetailed(Path.of("./data/input/ddm"));
        long expected = Files.list(Path.of("data/input/ddm"))
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .count();
        assertEquals(expected, r.getTotalFiles(), "./ 前缀的相对路径不应被隐藏过滤误杀");
        assertEquals(4, r.getEntities().size()); // customer_model.xml 含 CUSTOMER 与 ORDER 两个实体
        assertEquals(0, r.getFailedFiles().size());
        assertTrue(expected >= 3, "样例目录应至少有 3 个模型文件");
    }
}
