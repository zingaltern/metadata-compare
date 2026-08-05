package com.dqc.compare.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dqc.compare.dto.CompareReport;
import com.dqc.compare.dto.HistoryDiff;
import com.dqc.compare.entity.CompareResult;
import com.dqc.compare.entity.CompareTask;
import com.dqc.compare.entity.CompareTaskConfig;
import com.dqc.compare.mapper.CompareResultMapper;
import com.dqc.compare.mapper.CompareTaskConfigMapper;
import com.dqc.compare.mapper.CompareTaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 比对查询服务：任务列表、结果明细、历史对比（对应文档 八 REST API）。
 */
@Service
public class CompareQueryService {

    private final CompareTaskMapper taskMapper;
    private final CompareResultMapper resultMapper;
    private final CompareTaskConfigMapper configMapper;

    public CompareQueryService(CompareTaskMapper taskMapper, CompareResultMapper resultMapper,
                               CompareTaskConfigMapper configMapper) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.configMapper = configMapper;
    }

    public List<CompareTask> listTasks() {
        QueryWrapper<CompareTask> qw = new QueryWrapper<>();
        qw.orderByDesc("id");
        return taskMapper.selectList(qw);
    }

    /** 任务列表（分页，按 id 倒序；单页上限 500）。 */
    public List<CompareTask> listTasks(int page, int size) {
        int limit = Math.min(Math.max(1, size), 500);
        int offset = Math.max(1, page) == 1 ? 0 : (Math.max(1, page) - 1) * limit;
        QueryWrapper<CompareTask> qw = new QueryWrapper<>();
        qw.orderByDesc("id").last("LIMIT " + offset + ", " + limit);
        return taskMapper.selectList(qw);
    }

    /** 任务总数（分页展示用）。 */
    public long countTasks() {
        Long c = taskMapper.selectCount(new QueryWrapper<>());
        return c == null ? 0 : c;
    }

    public List<CompareResult> resultsByTask(Long taskId) {
        return resultsByTask(taskId, 1, 200);
    }

    /** 任务结果明细（分页，默认第 1 页 200 条，单页上限 500）。 */
    public List<CompareResult> resultsByTask(Long taskId, int page, int size) {
        QueryWrapper<CompareResult> qw = new QueryWrapper<>();
        int limit = Math.min(Math.max(1, size), 500);
        int offset = Math.max(1, page) == 1 ? 0 : (Math.max(1, page) - 1) * limit;
        qw.eq("task_id", taskId).orderByDesc("id").last("LIMIT " + offset + ", " + limit);
        return resultMapper.selectList(qw);
    }

    /**
     * 与上一个成功任务对比（新增/解决/持续）。
     */
    public HistoryDiff historyDiff(Long taskId) {
        CompareTask cur = taskMapper.selectById(taskId);
        List<CompareResult> current = resultsByTask(taskId);
        HistoryDiff diff = new HistoryDiff();
        if (cur == null) {
            diff.getAdded().addAll(current);
            return diff;
        }
        Long prevId = previousSuccessTaskId(cur);
        if (prevId == null) {
            diff.getAdded().addAll(current);
            return diff;
        }
        List<CompareResult> previous = resultsByTask(prevId);
        Set<String> currentKeys = current.stream().map(this::keyOf).collect(Collectors.toSet());
        Set<String> prevKeys = previous.stream().map(this::keyOf).collect(Collectors.toSet());

        for (CompareResult r : current) {
            if (!prevKeys.contains(keyOf(r))) {
                diff.getAdded().add(r);
            } else {
                diff.getPersistent().add(r);
            }
        }
        for (CompareResult r : previous) {
            if (!currentKeys.contains(keyOf(r))) {
                diff.getResolved().add(r);
            }
        }
        return diff;
    }

    private Long previousSuccessTaskId(CompareTask cur) {
        QueryWrapper<CompareTask> qw = new QueryWrapper<>();
        qw.eq("task_config_id", cur.getTaskConfigId())
                .eq("status", "SUCCESS")
                .lt("id", cur.getId())
                .orderByDesc("id")
                .last("LIMIT 1");
        List<CompareTask> list = taskMapper.selectList(qw);
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private String keyOf(CompareResult r) {
        return (r.getRuleName() == null ? "" : r.getRuleName()) + "|"
                + (r.getTableName() == null ? "" : r.getTableName()) + "|"
                + (r.getFieldName() == null ? "" : r.getFieldName()) + "|"
                + (r.getSeverity() == null ? "" : r.getSeverity());
    }
}
