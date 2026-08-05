package com.dqc.compare.ticket;

import com.dqc.compare.rule.RuleDef;

/**
 * 比对流水线在评估阶段累积的"待创建工单"请求，统一入库阶段再落库并通知。
 */
public record TicketRequest(RuleDef rule, String tableName, String fieldName, String message, String recipients) {
}
