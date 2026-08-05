package com.dqc.compare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 比对任务配置（对应文档 5.2 compare_task_config）。
 */
@Data
@TableName("compare_task_config")
public class CompareTaskConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private Boolean enabled;
    private String cronExpression;
    private String prodDdlPath;
    private String ddmPath;
    private String soaPath;
    private String fileSpecPath;
    private String recipients;
    private LocalDateTime lastRunTime;
}
