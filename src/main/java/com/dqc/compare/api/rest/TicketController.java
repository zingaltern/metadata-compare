package com.dqc.compare.api.rest;

import com.dqc.compare.dto.ReviewRequest;
import com.dqc.compare.entity.ReviewTicket;
import com.dqc.compare.ticket.ReviewTicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 复核工单 REST API（对应文档 八）。
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final ReviewTicketService ticketService;

    public TicketController(ReviewTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** 工单列表（支持状态筛选：PENDING/CONFIRMED/REJECTED/IGNORED） */
    @GetMapping
    public List<ReviewTicket> list(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "200") int size) {
        return ticketService.listByStatus(status, page, size);
    }

    /** 提交复核意见，更新工单状态 */
    @PutMapping("/{id}/review")
    public ResponseEntity<ReviewTicket> review(@PathVariable Long id, @RequestBody ReviewRequest req) {
        if (req.getStatus() == null || req.getStatus().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ReviewTicket t = ticketService.review(id, req.getStatus(), req.getReviewer(), req.getComment());
        return ResponseEntity.ok(t);
    }

    /** 工单详情 */
    @GetMapping("/{id}")
    public ResponseEntity<ReviewTicket> detail(@PathVariable Long id) {
        ReviewTicket t = ticketService.getById(id);
        return t == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(t);
    }
}
