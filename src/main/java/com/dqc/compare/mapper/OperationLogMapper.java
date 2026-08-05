package com.dqc.compare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dqc.compare.entity.OperationLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Delete("DELETE FROM operation_log WHERE create_time < #{cutoff}")
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
