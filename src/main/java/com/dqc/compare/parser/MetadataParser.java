package com.dqc.compare.parser;

import com.dqc.compare.model.StandardMetadata;

import java.util.List;

/**
 * 可插拔解析器接口（对应文档 4.1）。
 * 每种文件格式实现一个解析器并注册为 Spring Bean；新增格式无需修改主流程。
 */
public interface MetadataParser {

    /**
     * 是否支持该文件（基于后缀 + 内容特征）。
     */
    boolean supports(ParseRequest req);

    /**
     * 解析为统一中间格式列表（一个文件可能含多张表/多个实体）。
     */
    List<StandardMetadata> parse(ParseRequest req) throws Exception;
}
