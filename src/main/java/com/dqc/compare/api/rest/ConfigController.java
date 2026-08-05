package com.dqc.compare.api.rest;

import com.dqc.compare.dto.ConfigUpdateRequest;
import com.dqc.compare.entity.CompareTaskConfig;
import com.dqc.compare.mapper.CompareTaskConfigMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;

import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务配置 REST API（对应文档 八：修改 Cron 等无需重启）。
 */
@RestController
@RequestMapping("/api/config/tasks")
public class ConfigController {

    private final CompareTaskConfigMapper configMapper;

    public ConfigController(CompareTaskConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ConfigUpdateRequest req) {
        CompareTaskConfig c = configMapper.selectById(id);
        if (c == null) {
            return ResponseEntity.notFound().build();
        }
        if (req.getTaskName() != null) {
            c.setTaskName(req.getTaskName());
        }
        if (req.getEnabled() != null) {
            c.setEnabled(req.getEnabled());
        }
        if (req.getCronExpression() != null) {
            // 校验 cron 合法性，非法则拒绝，避免脏数据导致调度器静默跳过
            try {
                CronExpression.parse(req.getCronExpression());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cron 表达式非法：" + req.getCronExpression()));
            }
            c.setCronExpression(req.getCronExpression());
        }
        if (req.getProdDdlPath() != null) {
            c.setProdDdlPath(req.getProdDdlPath());
        }
        if (req.getDdmPath() != null) {
            c.setDdmPath(req.getDdmPath());
        }
        if (req.getSoaPath() != null) {
            c.setSoaPath(req.getSoaPath());
        }
        if (req.getFileSpecPath() != null) {
            c.setFileSpecPath(req.getFileSpecPath());
        }
        if (req.getRecipients() != null) {
            c.setRecipients(req.getRecipients());
        }
        configMapper.updateById(c);
        return ResponseEntity.ok(c);
    }
}
