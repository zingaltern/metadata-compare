package com.dqc.compare.dto;

import com.dqc.compare.model.SourceType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个数据源的解析健康度。
 *
 * <p>用途：让"某数据源解析失败 / 解析为空"这类问题在报告与邮件中显式可见，
 * 避免静默丢源导致比对结果"看起来一切正常"。</p>
 */
@Data
public class SourceHealth {

    private SourceType sourceType;
    /** 目录中尝试解析的文件总数 */
    private int fileCount;
    /** 成功解析出至少一个实体的文件数 */
    private int parsedFileCount;
    /** 解析成功但未产出实体的文件数 */
    private int emptyFileCount;
    /** 解析抛异常的文件数 */
    private int failedFileCount;
    /** 解析出的实体（表/接口）总数 */
    private int entityCount;
    /** 解析出的字段总数 */
    private int fieldCount;
    /** 失败 / 空结果的文件路径列表 */
    private List<String> failedFiles = new ArrayList<>();
    /** 健康提示（如"未解析出任何实体"） */
    private String warning;

    /** 该源是否健康：至少解析出 1 个实体且无失败文件 */
    public boolean isHealthy() {
        return entityCount > 0 && failedFileCount == 0;
    }
}
