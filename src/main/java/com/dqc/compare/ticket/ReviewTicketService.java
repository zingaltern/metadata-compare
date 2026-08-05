package com.dqc.compare.ticket;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dqc.compare.config.AppProperties;
import com.dqc.compare.entity.ReviewTicket;
import com.dqc.compare.mapper.ReviewTicketMapper;
import com.dqc.compare.notify.MailService;
import com.dqc.compare.rule.RuleDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 人工复核工单服务（对应文档 4.4）。
 * 状态流转：PENDING → CONFIRMED / REJECTED / IGNORED。
 */
@Service
public class ReviewTicketService {

    private static final Logger log = LoggerFactory.getLogger(ReviewTicketService.class);

    private final ReviewTicketMapper ticketMapper;
    private final MailService mailService;
    private final AppProperties appProperties;

    public ReviewTicketService(ReviewTicketMapper ticketMapper, MailService mailService, AppProperties appProperties) {
        this.ticketMapper = ticketMapper;
        this.mailService = mailService;
        this.appProperties = appProperties;
    }

    /**
     * 创建复核工单并通知复核人（仅当规则 action=CREATE_REVIEW_TICKET 时由流水线调用）。
     */
    public ReviewTicket createAndNotify(Long taskId, RuleDef rule, String tableName,
                                        String fieldName, String message, String recipients) {
        ReviewTicket t = new ReviewTicket();
        t.setTaskId(taskId);
        t.setSeverity(rule.getSeverity() == null ? "INFO" : rule.getSeverity().name());
        t.setTableName(tableName);
        t.setFieldName(fieldName);
        t.setMessage(message);
        t.setStatus("PENDING");
        t.setNotified(false);
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        // ticket_no 为 NOT NULL + UNIQUE：先用随机临时号插入（跨实例/并发下也绝不冲突），
        // 插入成功后用真实自增主键生成可读的最终工单号（RT-yyyyMMdd-NNNNN）。
        t.setTicketNo(generateTempTicketNo());
        ticketMapper.insert(t);
        t.setTicketNo(generateTicketNo(t.getId()));
        if (recipients != null && !recipients.isBlank()) {
            boolean sent = mailService.sendTicketNotification(recipients, t);
            t.setNotified(sent);
        }
        ticketMapper.updateById(t);
        log.info("创建复核工单 {}（规则={}, 表={}, 字段={}）", t.getTicketNo(), rule.getName(), tableName, fieldName);
        return t;
    }

    /**
     * 提交复核意见，更新工单状态。
     */
    public ReviewTicket review(Long id, String status, String reviewer, String comment) {
        ReviewTicket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new IllegalArgumentException("工单不存在: " + id);
        }
        if (!List.of("CONFIRMED", "REJECTED", "IGNORED").contains(status)) {
            throw new IllegalArgumentException("非法工单状态: " + status);
        }
        t.setStatus(status);
        t.setAssignee(reviewer);
        t.setReviewComment(comment);
        t.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(t);
        log.info("工单 {} 状态更新为 {}", t.getTicketNo(), status);
        return t;
    }

    public ReviewTicket getById(Long id) {
        return ticketMapper.selectById(id);
    }

    public List<ReviewTicket> listByStatus(String status) {
        return listByStatus(status, 1, 200);
    }

    /** 工单列表（分页，默认第 1 页 200 条，单页上限 500）。 */
    public List<ReviewTicket> listByStatus(String status, int page, int size) {
        QueryWrapper<ReviewTicket> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        int limit = Math.min(Math.max(1, size), 500);
        int offset = Math.max(1, page) == 1 ? 0 : (Math.max(1, page) - 1) * limit;
        qw.orderByDesc("id").last("LIMIT " + offset + ", " + limit);
        return ticketMapper.selectList(qw);
    }

    private String generateTicketNo(Long id) {
        String prefix = appProperties.getTicket().getNoPrefix();
        if (id == null) {
            id = 0L;
        }
        return String.format("%s-%s-%05d", prefix, LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), id);
    }

    private String generateTempTicketNo() {
        String prefix = appProperties.getTicket().getNoPrefix();
        return prefix + "-TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
