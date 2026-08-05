package com.dqc.compare.dto;

import com.dqc.compare.entity.CompareResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史对比结果（对应文档 4.5）：以「规则名+表名+字段名」为唯一标识对比上次成功任务。
 */
@Data
public class HistoryDiff {

    /** 新增问题：本次有、上次无 */
    private List<CompareResult> added = new ArrayList<>();
    /** 已解决问题：上次有、本次无 */
    private List<CompareResult> resolved = new ArrayList<>();
    /** 持续问题：两次都有 */
    private List<CompareResult> persistent = new ArrayList<>();

    public int getAddedCount() { return added.size(); }
    public int getResolvedCount() { return resolved.size(); }
    public int getPersistentCount() { return persistent.size(); }
}
