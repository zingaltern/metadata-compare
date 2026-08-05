package com.dqc.compare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行记录（对应文档 5.2 compare_task）。
 */
@Data
@TableName("compare_task")
public class CompareTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskConfigId;
    private String taskName;
    private String status;          // RUNNING / SUCCESS / FAILED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalCount;
    private Integer criticalCount;
    private Integer warningCount;
    private Integer infoCount;
    private Integer ticketCount;
    private String errorMessage;
}
