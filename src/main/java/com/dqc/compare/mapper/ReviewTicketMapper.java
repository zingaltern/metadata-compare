package com.dqc.compare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dqc.compare.entity.ReviewTicket;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ReviewTicketMapper extends BaseMapper<ReviewTicket> {

    @Delete("DELETE FROM review_ticket WHERE create_time < #{cutoff}")
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
