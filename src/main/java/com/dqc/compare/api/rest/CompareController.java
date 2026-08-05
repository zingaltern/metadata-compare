package com.dqc.compare.api.rest;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dqc.compare.dto.CompareReport;
import com.dqc.compare.dto.HistoryDiff;
import com.dqc.compare.entity.CompareResult;
import com.dqc.compare.entity.CompareTask;
import com.dqc.compare.mapper.CompareTaskConfigMapper;
import com.dqc.compare.service.ComparePipeline;
import com.dqc.compare.service.CompareQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 比对相关 REST API（对应文档 八）。
 */
@RestController
@RequestMapping("/api/compare")
public class CompareController {

    private final ComparePipeline pipeline;
    private final CompareQueryService queryService;
    private final CompareTaskConfigMapper configMapper;

    public CompareController(ComparePipeline pipeline, CompareQueryService queryService,
                             CompareTaskConfigMapper configMapper) {
        this.pipeline = pipeline;
        this.queryService = queryService;
        this.configMapper = configMapper;
    }

    /** 手动触发比对。configId 不传则触发所有启用任务。 */
    @PostMapping("/trigger")
    public ResponseEntity<?> trigger(@RequestParam(required = false) Long configId) {
        if (configId != null) {
            CompareReport report = pipeline.run(configId);
            return ResponseEntity.ok(report);
        }
        QueryWrapper<com.dqc.compare.entity.CompareTaskConfig> qw = new QueryWrapper<>();
        qw.eq("enabled", true);
        List<com.dqc.compare.entity.CompareTaskConfig> configs = configMapper.selectList(qw);
        List<CompareReport> reports = new ArrayList<>();
        for (com.dqc.compare.entity.CompareTaskConfig c : configs) {
            reports.add(pipeline.run(c));
        }
        return ResponseEntity.ok(reports);
    }

    /** 任务列表 */
    @GetMapping("/tasks")
    public ResponseEntity<List<CompareTask>> tasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(queryService.countTasks()))
                .body(queryService.listTasks(page, size));
    }

    /** 任务结果明细 */
    @GetMapping("/tasks/{id}/results")
    public List<CompareResult> results(@PathVariable Long id,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "200") int size) {
        return queryService.resultsByTask(id, page, size);
    }

    /** 历史对比（与上一成功任务） */
    @GetMapping("/tasks/{id}/history-diff")
    public HistoryDiff historyDiff(@PathVariable Long id) {
        return queryService.historyDiff(id);
    }
}
