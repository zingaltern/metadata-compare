package com.dqc.compare.dto;

import com.dqc.compare.entity.CompareResult;
import com.dqc.compare.entity.ReviewTicket;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次比对执行的汇总报告（流水线返回，非持久化实体）。
 */
@Data
public class CompareReport {

    private Long taskId;
    private String taskName;
    private String status;            // SUCCESS / FAILED
    private int totalCount;
    private int criticalCount;
    private int warningCount;
    private int infoCount;
    private int ticketCount;
    private long durationMs;
    private String errorMessage;
    /** 各数据源解析健康度（暴露"某源解析失败/为空"信号，避免静默丢源） */
    private List<SourceHealth> sourceHealth = new ArrayList<>();
    /** 源健康度摘要文本（用于邮件/快速查看） */
    private String sourceHealthSummary;
    private List<CompareResult> results = new ArrayList<>();
    private List<ReviewTicket> tickets = new ArrayList<>();
}
