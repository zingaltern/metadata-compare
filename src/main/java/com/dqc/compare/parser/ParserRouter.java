package com.dqc.compare.parser;

import com.dqc.compare.model.StandardMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 解析器路由（对应文档 4.1）。根据文件后缀 + 内容特征自动匹配解析器，按优先级排序。
 * 所有 MetadataParser 实现作为 Spring Bean 注入，新增格式只需新增实现类。
 */
@Component
public class ParserRouter {

    private static final Logger log = LoggerFactory.getLogger(ParserRouter.class);

    private static final List<Class<? extends MetadataParser>> PRIORITY = List.of(
            com.dqc.compare.parser.impl.MySqlDdlParser.class,
            com.dqc.compare.parser.impl.SwaggerApiParser.class,
            com.dqc.compare.parser.impl.JsonModelParser.class,
            com.dqc.compare.parser.impl.XmlModelParser.class,
            com.dqc.compare.parser.impl.ExcelSpecParser.class
    );

    private final List<MetadataParser> parsers;

    public ParserRouter(List<MetadataParser> parsers) {
        this.parsers = parsers.stream()
                .sorted(Comparator.comparingInt(p -> {
                    int idx = PRIORITY.indexOf(p.getClass());
                    return idx < 0 ? Integer.MAX_VALUE : idx;
                }))
                .toList();
    }

    /**
     * 解析单个文件为统一中间格式列表。无匹配解析器时返回空列表。
     */
    public List<StandardMetadata> parse(Path file) throws Exception {
        ParseRequest req = new ParseRequest(file);
        for (MetadataParser parser : parsers) {
            if (parser.supports(req)) {
                log.debug("路由文件 {} -> {}", file.getFileName(), parser.getClass().getSimpleName());
                return parser.parse(req);
            }
        }
        log.warn("未找到匹配的解析器，已跳过: {}", file.getFileName());
        return List.of();
    }

    /**
     * 解析目录下所有文件（递归子目录），合并为统一中间格式列表。
     */
    public List<StandardMetadata> parseDirectory(Path dir) throws Exception {
        return parseDirectoryDetailed(dir).getEntities();
    }

    /**
     * 解析目录下所有文件（递归子目录），返回实体 + 健康统计。
     * 失败文件与"解析成功但无实体"的文件会被单独列出，供上层构建源健康度。
     */
    public ParseDirectoryResult parseDirectoryDetailed(Path dir) {
        List<StandardMetadata> all = new java.util.ArrayList<>();
        List<String> failedFiles = new java.util.ArrayList<>();
        List<String> emptyFiles = new java.util.ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return new ParseDirectoryResult(all, failedFiles, emptyFiles, 0);
        }
        List<Path> files;
        try (var stream = Files.walk(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (Exception e) {
            log.warn("扫描目录失败：{} -> {}", dir, e.getMessage());
            return new ParseDirectoryResult(all, failedFiles, emptyFiles, 0);
        }
        for (Path f : files) {
            try {
                List<StandardMetadata> parsed = parse(f);
                if (parsed.isEmpty()) {
                    emptyFiles.add(f.toString());
                } else {
                    all.addAll(parsed);
                }
            } catch (Exception e) {
                log.warn("解析文件失败，已跳过该文件（不影响其他文件）：{} -> {}", f.getFileName(), e.getMessage());
                failedFiles.add(f.toString());
            }
        }
        return new ParseDirectoryResult(all, failedFiles, emptyFiles, files.size());
    }
}
