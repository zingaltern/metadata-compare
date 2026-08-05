package com.dqc.compare.parser.impl;

import com.dqc.compare.model.FieldMeta;
import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.MetadataParser;
import com.dqc.compare.parser.ParseRequest;
import com.dqc.compare.parser.impl.JsonModelParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SOA 接口文档解析器（.json / .yaml OpenAPI），基于 swagger-parser。
 */
@Component
public class SwaggerApiParser implements MetadataParser {

    private static final Logger log = LoggerFactory.getLogger(SwaggerApiParser.class);

    @Override
    public boolean supports(ParseRequest req) {
        String name = req.getFileName().toLowerCase();
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return true;
        }
        if (name.endsWith(".json")) {
            return JsonModelParser.looksLikeSwagger(req.getContentSnippet());
        }
        return false;
    }

    @Override
    public List<StandardMetadata> parse(ParseRequest req) throws Exception {
        List<StandardMetadata> result = new ArrayList<>();
        try {
            String content = Files.readString(req.getPath());
            SwaggerParseResult spr = new OpenAPIV3Parser().readContents(content);
            OpenAPI openAPI = spr.getOpenAPI();
            if (openAPI == null || openAPI.getComponents() == null
                    || openAPI.getComponents().getSchemas() == null) {
                // Swagger 2.x（swagger: "2.0" + definitions）需经 V2 解析器 + SwaggerConverter 转换
                SwaggerParseResult v2 = new SwaggerConverter().readContents(content, null, new ParseOptions());
                openAPI = v2.getOpenAPI();
                if (openAPI != null) {
                    log.debug("Swagger 2 文档已转换：{}", req.getFileName());
                }
            }
            if (openAPI == null || openAPI.getComponents() == null
                    || openAPI.getComponents().getSchemas() == null) {
                log.warn("OpenAPI 未解析出 schemas，已跳过: {}", req.getFileName());
                return result;
            }
            for (Map.Entry<String, Schema> entry : openAPI.getComponents().getSchemas().entrySet()) {
                result.add(toStandard(entry.getKey(), entry.getValue()));
            }
            log.info("SOA 文档解析完成：{} -> {} 个接口模型", req.getFileName(), result.size());
        } catch (Exception e) {
            log.warn("解析 SOA 文档失败，已跳过: {} -> {}", req.getFileName(), e.getMessage());
        }
        return result;
    }

    private StandardMetadata toStandard(String schemaName, Schema<?> schema) {
        StandardMetadata meta = new StandardMetadata(SourceType.SOA_API, schemaName);
        // 下发方式（来自 schema 级扩展 x-deliveryType）
        if (schema.getExtensions() != null) {
            Object dt = schema.getExtensions().get("x-deliveryType");
            if (dt != null) {
                meta.setDeliveryType(String.valueOf(dt));
            }
        }
        Map<String, Schema> props = schema.getProperties();
        if (props != null) {
            for (Map.Entry<String, Schema> p : props.entrySet()) {
                meta.getFields().add(toField(p.getKey(), p.getValue()));
            }
        }
        return meta;
    }

    private FieldMeta toField(String name, Schema<?> prop) {
        FieldMeta fm = new FieldMeta();
        fm.setFieldName(name);
        String type = prop.getType();
        String format = prop.getFormat();
        fm.setDataType(format != null ? (type + ":" + format) : type);
        if ("string".equals(type) && prop.getMaxLength() != null) {
            fm.setLength(prop.getMaxLength());
        }
        fm.setNullable(prop.getNullable() == null || prop.getNullable());
        fm.setComment(prop.getDescription());
        return fm;
    }
}
