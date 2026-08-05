package com.dqc.compare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dqc.compare.entity.CompareTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CompareTaskMapper extends BaseMapper<CompareTask> {

    /** 查询早于截止时间的任务 id（保留策略清理用，单次最多 5000 条）。 */
    @Select("SELECT id FROM compare_task WHERE start_time < #{cutoff} LIMIT 5000")
    List<Long> selectObsoleteIds(@Param("cutoff") LocalDateTime cutoff);
}
