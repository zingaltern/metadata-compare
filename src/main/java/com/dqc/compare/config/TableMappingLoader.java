package com.dqc.compare.config;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表名映射加载器（与 RuleLoader 同样的"拷贝 + 热加载"模式）。
 * <ul>
 *   <li>首次启动：若文件系统映射文件不存在，从 classpath 拷贝，便于编辑后热加载</li>
 *   <li>定时检测变更并热加载（默认 60 秒）</li>
 *   <li>提供 {@link #resolveLogicalName(String)} 把任意来源的原始表名归一到逻辑表名</li>
 * </ul>
 */
@Component
public class TableMappingLoader {

    private static final Logger log = LoggerFactory.getLogger(TableMappingLoader.class);
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    private final AppProperties appProperties;
    private volatile Map<String, String> aliasToLogical = new ConcurrentHashMap<>();
    /** 逻辑表名 -> (原始字段名 -> 逻辑字段名) */
    private volatile Map<String, Map<String, String>> tableFieldAlias = new ConcurrentHashMap<>();
    private volatile long lastModified = -1;

    public TableMappingLoader(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        ensureFileExists();
        reload();
    }

    @Scheduled(fixedDelayString = "${app.table-mapping.hot-reload-seconds:60}000")
    public void scheduledReload() {
        if (isClasspathOnly()) {
            return;
        }
        Path p = Paths.get(appProperties.getTableMapping().getPath());
        try {
            long mod = Files.getLastModifiedTime(p).toMillis();
            if (mod == lastModified) {
                return;
            }
            doReload(p, mod);
        } catch (Exception e) {
            log.warn("检测/热加载表名映射失败: {}", e.getMessage());
        }
    }

    /**
     * 把原始表名归一到逻辑表名；未配置映射时原样返回（保持原有行为）。
     */
    public String resolveLogicalName(String rawTableName) {
        if (rawTableName == null) {
            return null;
        }
        String n = normalize(rawTableName);
        return aliasToLogical.getOrDefault(n, rawTableName);
    }

    /**
     * 把某逻辑表下的原始字段名归一到逻辑字段名；未配置映射时返回 null（调用方回退为原名归一）。
     */
    public String resolveFieldName(String logicalTable, String rawFieldName) {
        if (logicalTable == null || rawFieldName == null) {
            return null;
        }
        Map<String, String> fmap = tableFieldAlias.get(normalize(logicalTable));
        if (fmap == null) {
            return null;
        }
        return fmap.get(normalize(rawFieldName));
    }

    public boolean isEmpty() {
        return aliasToLogical.isEmpty();
    }

    private boolean isClasspathOnly() {
        return appProperties.getTableMapping().getPath().startsWith("classpath:");
    }

    private void ensureFileExists() {
        if (isClasspathOnly()) {
            return;
        }
        Path p = Paths.get(appProperties.getTableMapping().getPath());
        if (Files.exists(p)) {
            return;
        }
        try {
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            try (InputStream in = new ClassPathResource("rules/table-mappings.yml").getInputStream()) {
                Files.copy(in, p);
            }
            log.info("已从 classpath 拷贝表名映射文件到 {}，可编辑后自动热加载", p);
        } catch (Exception e) {
            log.warn("拷贝表名映射文件失败: {}", e.getMessage());
        }
    }

    private void reload() {
        if (isClasspathOnly()) {
            String res = appProperties.getTableMapping().getPath().substring("classpath:".length());
            try (InputStream in = new ClassPathResource(res).getInputStream()) {
                doLoad(in);
            } catch (Exception e) {
                log.error("加载 classpath 表名映射失败: {}", e.getMessage());
            }
            return;
        }
        Path p = Paths.get(appProperties.getTableMapping().getPath());
        try {
            long mod = Files.getLastModifiedTime(p).toMillis();
            doReload(p, mod);
        } catch (Exception e) {
            log.warn("加载表名映射文件失败: {}", e.getMessage());
        }
    }

    private void doReload(Path p, long mod) {
        try (InputStream in = Files.newInputStream(p)) {
            doLoad(in);
            lastModified = mod;
        } catch (Exception e) {
            log.warn("重新加载表名映射失败，沿用旧映射: {}", e.getMessage());
        }
    }

    private void doLoad(InputStream in) throws Exception {
        TableMappingConfig cfg = YAML_MAPPER.readValue(in, TableMappingConfig.class);
        Map<String, String> map = new ConcurrentHashMap<>();
        Map<String, Map<String, String>> fieldMap = new ConcurrentHashMap<>();
        if (cfg.getMappings() != null) {
            for (MappingEntry e : cfg.getMappings()) {
                if (e.getLogicalName() == null || e.getAliases() == null) {
                    continue;
                }
                String logical = normalize(e.getLogicalName());
                for (String alias : e.getAliases()) {
                    map.put(normalize(alias), logical);
                }
                // 字段级映射：逻辑字段名自身也映射到自身，别名映射到逻辑字段名
                Map<String, String> fmap = new ConcurrentHashMap<>();
                if (e.getFields() != null) {
                    for (FieldMappingEntry fe : e.getFields()) {
                        if (fe.getLogicalName() == null) {
                            continue;
                        }
                        String lf = normalize(fe.getLogicalName());
                        fmap.put(lf, lf);
                        if (fe.getAliases() != null) {
                            for (String a : fe.getAliases()) {
                                fmap.put(normalize(a), lf);
                            }
                        }
                    }
                }
                fieldMap.put(logical, fmap);
            }
        }
        aliasToLogical = map;
        tableFieldAlias = fieldMap;
        int fieldCount = fieldMap.values().stream().mapToInt(Map::size).sum();
        log.info("表名映射已加载/热更新，共 {} 条逻辑表映射、{} 条字段映射", map.size(), fieldCount);
    }

    private String normalize(String s) {
        return s == null ? "" : s.replace("`", "").trim().toUpperCase();
    }
}
