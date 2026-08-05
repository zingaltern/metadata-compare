package com.dqc.compare.parser.impl;

import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.MetadataParser;
import com.dqc.compare.parser.ParseRequest;
import com.dqc.compare.parser.impl.model.DdmModel;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * DDM 模型解析器（.xml），使用 Jackson XML（对应文档 4.1 的 JAXB/DOM 路线）。
 */
@Component
public class XmlModelParser implements MetadataParser {

    private static final Logger log = LoggerFactory.getLogger(XmlModelParser.class);
    private static final XmlMapper XML_MAPPER = XmlMapper.builder().build();

    @Override
    public boolean supports(ParseRequest req) {
        return req.getFileName().toLowerCase().endsWith(".xml");
    }

    @Override
    public List<StandardMetadata> parse(ParseRequest req) throws Exception {
        List<StandardMetadata> result = new ArrayList<>();
        try {
            String content = Files.readString(req.getPath());
            DdmModel.DdmXmlRoot root = XML_MAPPER.readValue(content, DdmModel.DdmXmlRoot.class);
            if (root.entities != null) {
                for (DdmModel.DdmXmlEntity e : root.entities) {
                    if (e.name != null) {
                        result.add(DdmModel.toStandard(e, SourceType.DDM_MODEL));
                    }
                }
            }
            log.info("DDM XML 解析完成：{} -> {} 个实体", req.getFileName(), result.size());
        } catch (Exception e) {
            log.warn("解析 DDM XML 失败，已跳过: {} -> {}", req.getFileName(), e.getMessage());
        }
        return result;
    }
}
