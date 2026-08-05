package com.dqc.compare.parser;

import com.dqc.compare.model.StandardMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录解析结果：解析出的实体 + 健康统计（失败文件、空结果文件、文件总数）。
 * 供流水线构建源健康度报告，暴露"某文件未参与比对"的信号。
 */
public class ParseDirectoryResult {

    private final List<StandardMetadata> entities;
    /** 解析抛异常的文件 */
    private final List<String> failedFiles;
    /** 解析成功但未产出任何实体的文件 */
    private final List<String> emptyFiles;
    private final int totalFiles;

    public ParseDirectoryResult(List<StandardMetadata> entities, List<String> failedFiles,
                                List<String> emptyFiles, int totalFiles) {
        this.entities = entities == null ? new ArrayList<>() : entities;
        this.failedFiles = failedFiles == null ? new ArrayList<>() : failedFiles;
        this.emptyFiles = emptyFiles == null ? new ArrayList<>() : emptyFiles;
        this.totalFiles = totalFiles;
    }

    public List<StandardMetadata> getEntities() {
        return entities;
    }

    public List<String> getFailedFiles() {
        return failedFiles;
    }

    public List<String> getEmptyFiles() {
        return emptyFiles;
    }

    public int getTotalFiles() {
        return totalFiles;
    }
}
