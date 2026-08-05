package com.dqc.compare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工复核工单（对应文档 5.2 review_ticket）。
 */
@Data
@TableName("review_ticket")
public class ReviewTicket {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;        // RT-20260727-001
    private Long taskId;
    private String severity;
    private String tableName;
    private String fieldName;
    private String message;
    private String status;          // PENDING / CONFIRMED / REJECTED / IGNORED
    private String assignee;
    private String reviewComment;
    private Boolean notified;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
