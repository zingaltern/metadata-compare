package com.dqc.compare.dto;

import lombok.Data;

/**
 * 复核工单提交请求体。
 */
@Data
public class ReviewRequest {
    /** CONFIRMED / REJECTED / IGNORED */
    private String status;
    private String reviewer;
    private String comment;
}
