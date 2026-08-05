package com.dqc.compare.dto;

import lombok.Data;

/**
 * 任务配置更新请求体（对应文档 4.6：修改 Cron 等无需重启）。
 */
@Data
public class ConfigUpdateRequest {
    private String taskName;
    private Boolean enabled;
    private String cronExpression;
    private String prodDdlPath;
    private String ddmPath;
    private String soaPath;
    private String fileSpecPath;
    private String recipients;
}
