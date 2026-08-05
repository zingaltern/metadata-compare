package com.dqc.compare.api.rest;

import com.dqc.compare.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传 / 列表 / 删除（一期收尾：让业务人员可在网页自助替换元数据文件）。
 * 按来源落到对应输入目录，与比对流水线读取路径一致：
 *   production -> {baseDir}/production/latest
 *   ddm        -> {baseDir}/ddm
 *   soa        -> {baseDir}/soa
 *   file_spec  -> {baseDir}/file_spec
 */
@RestController
@RequestMapping("/api/files")
public class FileApiController {

    private static final Logger log = LoggerFactory.getLogger(FileApiController.class);

    private static final Map<String, String> SOURCE_DIRS = Map.of(
            "production", "production/latest",
            "ddm", "ddm",
            "soa", "soa",
            "file_spec", "file_spec");

    private final AppProperties appProperties;

    public FileApiController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("source") String source,
                                    @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }
        String dirRel = SOURCE_DIRS.get(source);
        if (dirRel == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "非法来源：" + source
                    + "（应为 production/ddm/soa/file_spec）"));
        }
        String cleanName = sanitize(file.getOriginalFilename());
        if (cleanName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件名不合法"));
        }
        try {
            Path dir = Paths.get(appProperties.getInput().getBaseDir(), dirRel);
            Files.createDirectories(dir);
            Path target = dir.resolve(cleanName);
            if (Files.exists(target) && !overwrite) {
                return ResponseEntity.status(409).body(Map.of("error",
                        "文件已存在（" + cleanName + "），如需覆盖请勾选「覆盖」"));
            }
            file.transferTo(target);
            log.info("文件已上传：源={} 路径={}", source, target);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("source", source);
            resp.put("name", cleanName);
            resp.put("size", Files.size(target));
            resp.put("path", target.toString());
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "上传失败：" + e.getMessage()));
        }
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam("source") String source) {
        String dirRel = SOURCE_DIRS.get(source);
        List<Map<String, Object>> result = new ArrayList<>();
        if (dirRel == null) {
            return result;
        }
        Path dir = Paths.get(appProperties.getInput().getBaseDir(), dirRel);
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .forEach(p -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", p.getFileName().toString());
                        try {
                            m.put("size", Files.size(p));
                            m.put("modified", LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()), ZoneId.systemDefault()).toString());
                        } catch (IOException ignored) {
                        }
                        result.add(m);
                    });
        } catch (IOException e) {
            log.warn("列出文件失败：{}", e.getMessage());
        }
        return result;
    }

    @DeleteMapping
    public ResponseEntity<?> delete(@RequestParam("source") String source,
                                     @RequestParam("name") String name) {
        String dirRel = SOURCE_DIRS.get(source);
        if (dirRel == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "非法来源：" + source));
        }
        String cleanName = sanitize(name);
        if (cleanName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件名不合法"));
        }
        Path target = Paths.get(appProperties.getInput().getBaseDir(), dirRel, cleanName);
        try {
            if (!Files.exists(target)) {
                return ResponseEntity.status(404).body(Map.of("error", "文件不存在：" + cleanName));
            }
            Files.delete(target);
            log.info("文件已删除：源={} 文件={}", source, cleanName);
            return ResponseEntity.ok(Map.of("deleted", cleanName));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "删除失败：" + e.getMessage()));
        }
    }

    /** 仅保留基础文件名，杜绝路径穿越（../、绝对路径、非法字符）。 */
    private String sanitize(String original) {
        if (original == null) {
            return null;
        }
        String name = Paths.get(original).getFileName().toString();
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        return name;
    }
}
