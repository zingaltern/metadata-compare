package com.dqc.compare.model;

/**
 * 来源类型。对应统一中间格式 sourceType。
 */
public enum SourceType {
    PRODUCTION_DDL,  // 生产 DDL
    DDM_MODEL,       // DDM 模型
    SOA_API,         // SOA 接口文档
    FILE_SPEC        // 文件规范
}
