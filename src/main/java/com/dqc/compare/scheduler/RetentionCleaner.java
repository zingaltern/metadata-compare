package com.dqc.compare.scheduler;

import com.dqc.compare.config.AppProperties;
import com.dqc.compare.mapper.CompareResultMapper;
import com.dqc.compare.mapper.CompareTaskMapper;
import com.dqc.compare.mapper.OperationLogMapper;
import com.dqc.compare.mapper.ReviewTicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据保留策略清理：每天 03:30 执行一次。
 * 任务/结果按 app.retention.task-days 清理（0 = 不清理）；工单按 app.retention.ticket-days（0 = 永久保留）。
 */
@Component
public class RetentionCleaner {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleaner.class);

    private final AppProperties appProperties;
    private final CompareTaskMapper taskMapper;
    private final CompareResultMapper resultMapper;
    private final OperationLogMapper operationLogMapper;
    private final ReviewTicketMapper ticketMapper;

    public RetentionCleaner(AppProperties appProperties, CompareTaskMapper taskMapper,
                            CompareResultMapper resultMapper, OperationLogMapper operationLogMapper,
                            ReviewTicketMapper ticketMapper) {
        this.appProperties = appProperties;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.operationLogMapper = operationLogMapper;
        this.ticketMapper = ticketMapper;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void clean() {
        int taskDays = appProperties.getRetention().getTaskDays();
        if (taskDays > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(taskDays);
            List<Long> oldTaskIds = taskMapper.selectObsoleteIds(cutoff);
            if (!oldTaskIds.isEmpty()) {
                resultMapper.deleteByTaskIds(oldTaskIds);
                int removed = taskMapper.deleteBatchIds(oldTaskIds);
                log.info("数据保留清理：删除 {} 条超过 {} 天的任务（含对应结果）", removed, taskDays);
            }
            int logs = operationLogMapper.deleteBefore(cutoff);
            if (logs > 0) {
                log.info("数据保留清理：删除 {} 条超过 {} 天的操作日志", logs, taskDays);
            }
        }
        int ticketDays = appProperties.getRetention().getTicketDays();
        if (ticketDays > 0) {
            int removed = ticketMapper.deleteBefore(LocalDateTime.now().minusDays(ticketDays));
            if (removed > 0) {
                log.info("数据保留清理：删除 {} 条超过 {} 天的工单", removed, ticketDays);
            }
        }
    }
}
