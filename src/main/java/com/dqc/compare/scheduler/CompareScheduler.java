package com.dqc.compare.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dqc.compare.config.AppProperties;
import com.dqc.compare.entity.CompareTaskConfig;
import com.dqc.compare.mapper.CompareTaskConfigMapper;
import com.dqc.compare.service.ComparePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时调度（对应文档 4.6）。
 * 每 N 秒扫描一次启用的任务配置，依据其 Cron 表达式与上次执行时间判断到期并触发比对。
 * 修改数据库中的 Cron 即可调整频率，无需重启。
 */
@Component
public class CompareScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompareScheduler.class);

    private final CompareTaskConfigMapper configMapper;
    private final ComparePipeline pipeline;
    private final AppProperties appProperties;

    public CompareScheduler(CompareTaskConfigMapper configMapper, ComparePipeline pipeline, AppProperties appProperties) {
        this.configMapper = configMapper;
        this.pipeline = pipeline;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.poll-seconds:30}000")
    public void poll() {
        try {
            QueryWrapper<CompareTaskConfig> qw = new QueryWrapper<>();
            qw.eq("enabled", true);
            for (CompareTaskConfig config : configMapper.selectList(qw)) {
                if (isDue(config)) {
                    log.info("定时任务到期，触发比对：{}", config.getTaskName());
                    try {
                        pipeline.run(config);
                    } catch (Exception e) {
                        log.error("定时比对执行异常：{} -> {}", config.getTaskName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("调度轮询异常：{}", e.getMessage());
        }
    }

    private boolean isDue(CompareTaskConfig config) {
        if (config.getCronExpression() == null || config.getCronExpression().isBlank()) {
            return false;
        }
        CronExpression ce;
        try {
            ce = CronExpression.parse(config.getCronExpression());
        } catch (Exception e) {
            log.warn("任务 {} 的 Cron 表达式非法：{}", config.getTaskName(), config.getCronExpression());
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = config.getLastRunTime();
        // 首次运行（lastRunTime 为空）立即触发一次；之后依据 Cron 的“下次触发点”判断是否到期。
        if (last == null) {
            return true;
        }
        LocalDateTime next = ce.next(last);
        if (next == null) {
            return false;
        }
        boolean due = !now.isBefore(next);
        if (due) {
            log.debug("任务 {} 下次触发点 {} 已到（当前 {}），触发比对", config.getTaskName(), next, now);
        }
        return due;
    }
}
