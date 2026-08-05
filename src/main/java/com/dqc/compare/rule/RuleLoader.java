package com.dqc.compare.rule;

import com.dqc.compare.config.AppProperties;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则加载器（对应文档 4.3 热加载）。
 * <ul>
 *   <li>首次启动：若文件系统规则文件不存在，则从 classpath 拷贝，以便后续可热编辑</li>
 *   <li>定时（默认每分钟）检测文件系统规则文件变更并热加载</li>
 *   <li>classpath: 路径仅加载、不热更</li>
 * </ul>
 */
@Component
public class RuleLoader {

    private static final Logger log = LoggerFactory.getLogger(RuleLoader.class);
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    private final AppProperties appProperties;
    private final AtomicReference<List<RuleDef>> rules = new AtomicReference<>(List.of());
    private volatile long lastModified = -1;

    public RuleLoader(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        ensureFileExists();
        reload();
    }

    @Scheduled(fixedDelayString = "${app.rules.hot-reload-seconds:60}000")
    public void scheduledReload() {
        if (isClasspathOnly()) {
            return;
        }
        Path p = Paths.get(appProperties.getRules().getPath());
        try {
            long mod = Files.getLastModifiedTime(p).toMillis();
            if (mod == lastModified) {
                return;
            }
            doReload(p, mod);
        } catch (Exception e) {
            log.warn("检测/热加载规则文件失败: {}", e.getMessage());
        }
    }

    public List<RuleDef> getRules() {
        return rules.get();
    }

    private boolean isClasspathOnly() {
        return appProperties.getRules().getPath().startsWith("classpath:");
    }

    private void ensureFileExists() {
        if (isClasspathOnly()) {
            return;
        }
        Path p = Paths.get(appProperties.getRules().getPath());
        if (Files.exists(p)) {
            return;
        }
        try {
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            try (InputStream in = new ClassPathResource("rules/compare-rules.yml").getInputStream()) {
                Files.copy(in, p);
            }
            log.info("已从 classpath 拷贝规则文件到 {}，可编辑后自动热加载", p);
        } catch (Exception e) {
            log.warn("拷贝规则文件失败: {}", e.getMessage());
        }
    }

    private void reload() {
        if (isClasspathOnly()) {
            String res = appProperties.getRules().getPath().substring("classpath:".length());
            try (InputStream in = new ClassPathResource(res).getInputStream()) {
                doLoad(in);
            } catch (Exception e) {
                log.error("加载 classpath 规则失败: {}", e.getMessage());
            }
            return;
        }
        Path p = Paths.get(appProperties.getRules().getPath());
        try {
            long mod = Files.getLastModifiedTime(p).toMillis();
            doReload(p, mod);
        } catch (Exception e) {
            log.warn("加载规则文件失败: {}", e.getMessage());
        }
    }

    private void doReload(Path p, long mod) {
        try (InputStream in = Files.newInputStream(p)) {
            List<RuleDef> before = rules.get();
            doLoad(in);
            lastModified = mod;
            log.info("规则已加载/热更新，共 {} 条", rules.get().size());
            if (rules.get().size() != before.size()) {
                log.info("规则数量变化：{} -> {}", before.size(), rules.get().size());
            }
        } catch (Exception e) {
            log.warn("重新加载规则失败，沿用旧规则: {}", e.getMessage());
        }
    }

    private void doLoad(InputStream in) throws Exception {
        RulesConfig cfg = YAML_MAPPER.readValue(in, RulesConfig.class);
        List<RuleDef> raw = cfg.getRules() == null ? List.of() : cfg.getRules();
        List<RuleDef> valid = new ArrayList<>();
        int skipped = 0;
        for (RuleDef r : raw) {
            if (isValid(r)) {
                valid.add(r);
            } else {
                skipped++;
                log.warn("跳过非法规则（缺失 name/severity/action/condition）：{}", r == null ? "null" : r.getName());
            }
        }
        rules.set(valid);
        if (skipped > 0) {
            log.warn("规则加载完成：有效 {} 条，已跳过非法 {} 条", valid.size(), skipped);
        }
    }

    /** 校验规则必要字段，避免运行期因 null 触发 NPE。 */
    private boolean isValid(RuleDef r) {
        if (r == null) {
            return false;
        }
        if (r.getName() == null || r.getName().isBlank()) {
            return false;
        }
        if (r.getSeverity() == null) {
            return false;
        }
        if (r.getAction() == null) {
            return false;
        }
        if (r.getCondition() == null || r.getCondition().isBlank()) {
            return false;
        }
        return true;
    }
}
