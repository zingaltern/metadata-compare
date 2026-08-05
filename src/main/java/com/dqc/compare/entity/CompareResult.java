package com.dqc.compare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 比对结果明细（对应文档 5.2 compare_result）。
 */
@Data
@TableName("compare_result")
public class CompareResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String ruleName;
    private String category;        // VIOLATION / STRUCTURE / LOGIC
    private String severity;        // CRITICAL / WARNING / INFO
    private String tableName;
    private String fieldName;
    private String message;
    private String prodValue;
    private String modelValue;
    private String traceInfo;       // QLExpress 评估变量快照（规则归因）
}
