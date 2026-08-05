package com.dqc.compare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dqc.compare.entity.CompareResult;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompareResultMapper extends BaseMapper<CompareResult> {

    /**
     * 批量插入比对结果明细（单条 SQL，避免逐条 N+1）。
     */
    @Insert("<script>INSERT INTO compare_result "
            + "(task_id, rule_name, category, severity, table_name, field_name, message, prod_value, model_value, trace_info) VALUES "
            + "<foreach collection='list' item='r' separator=','>"
            + "(#{r.taskId}, #{r.ruleName}, #{r.category}, #{r.severity}, #{r.tableName}, "
            + "#{r.fieldName}, #{r.message}, #{r.prodValue}, #{r.modelValue}, #{r.traceInfo})"
            + "</foreach></script>")
    void insertBatch(@Param("list") List<CompareResult> list);
}
