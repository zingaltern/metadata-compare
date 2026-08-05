package com.dqc.compare.parser.impl;

import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.MetadataParser;
import com.dqc.compare.parser.ParseRequest;
import com.dqc.compare.parser.impl.model.DdmModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * DDM 模型解析器（.json），使用 Jackson。
 * 仅处理非 OpenAPI 的 DDM 模型 JSON；OpenAPI 文档由 SwaggerApiParser 处理。
 */
@Component
public class JsonModelParser implements MetadataParser {

    private static final Logger log = LoggerFactory.getLogger(JsonModelParser.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Override
    public boolean supports(ParseRequest req) {
        if (!req.getFileName().toLowerCase().endsWith(".json")) {
            return false;
        }
        // OpenAPI/Swagger 文档交给 SwaggerApiParser
        return !looksLikeSwagger(req.getContentSnippet());
    }

    static boolean looksLikeSwagger(String snippet) {
        if (snippet == null) {
            return false;
        }
        String s = snippet.toLowerCase();
        return s.contains("\"openapi\"")
                || s.contains("\"swagger\"")
                || s.contains("\"paths\"")
                || s.contains("swagger:");   // YAML 形态
    }

    @Override
    public List<StandardMetadata> parse(ParseRequest req) throws Exception {
        List<StandardMetadata> result = new ArrayList<>();
        try {
            String content = Files.readString(req.getPath());
            JsonNode root = JSON_MAPPER.readTree(content);
            // 兼容常见的根键：entities / models / tables
            for (String key : new String[]{"entities", "models", "tables"}) {
                JsonNode arr = root.get(key);
                if (arr == null || !arr.isArray()) {
                    continue;
                }
                for (JsonNode node : arr) {
                    DdmModel.DdmJsonEntity e = JSON_MAPPER.treeToValue(node, DdmModel.DdmJsonEntity.class);
                    if (e != null && e.name != null) {
                        result.add(DdmModel.toStandard(e, SourceType.DDM_MODEL));
                    }
                }
                break;
            }
            log.info("DDM JSON 解析完成：{} -> {} 个实体", req.getFileName(), result.size());
        } catch (Exception e) {
            log.warn("解析 DDM JSON 失败，已跳过: {} -> {}", req.getFileName(), e.getMessage());
        }
        return result;
    }
}
