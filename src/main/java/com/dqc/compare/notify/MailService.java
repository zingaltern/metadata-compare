package com.dqc.compare.notify;

import com.dqc.compare.entity.ReviewTicket;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 邮件通知服务（对应文档 4.4 / 3.2）。
 * 使用 Spring Mail + Freemarker HTML 模板。SMTP 未配置时优雅降级（仅记录日志，不抛异常）。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final Configuration freemarker;
    private boolean enabled;
    /** 邮件发送线程池：比对主流程不等待邮件，触发后立即返回 */
    private final ExecutorService mailExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mail-worker");
        t.setDaemon(true);
        return t;
    });

    public MailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.freemarker = new Configuration(Configuration.VERSION_2_3_32);
        this.freemarker.setClassForTemplateLoading(MailService.class, "/templates");
        this.freemarker.setDefaultEncoding(StandardCharsets.UTF_8.name());
    }

    @PostConstruct
    public void init() {
        this.enabled = mailProperties.getHost() != null && !mailProperties.getHost().isBlank();
        if (!enabled) {
            log.warn("SMTP 未配置（spring.mail.host 为空），邮件通知将降级为仅记录日志。"
                    + " 启用发信请在启动时注入环境变量：MAIL_HOST、MAIL_USER、MAIL_PASSWORD，"
                    + " 并配置收件人 APP_NOTIFY_RECIPIENTS（或 app.notify.recipients）。");
        } else {
            log.info("SMTP 已配置：{}，邮件通知已启用。", mailProperties.getHost());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 发送复核工单通知邮件。返回是否成功发送。
     */
    public boolean sendTicketNotification(String to, ReviewTicket ticket) {
        if (!enabled) {
            log.info("[邮件降级] 工单 {} 需通知复核人 {}（SMTP 未配置，已记录）", ticket.getTicketNo(), to);
            return false;
        }
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("ticket", ticket);
            model.put("appName", "元数据自动化比对系统");
            Template tpl = freemarker.getTemplate("ticket-notify.ftl");
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(tpl, model);

            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
            helper.setTo(split(to));
            helper.setSubject("【元数据比对】待复核工单 " + ticket.getTicketNo());
            helper.setText(html, true);
            if (mailProperties.getUsername() != null && !mailProperties.getUsername().isBlank()) {
                helper.setFrom(mailProperties.getUsername());
            }
            mailSender.send(msg);
            log.info("工单邮件已发送：{} -> {}", ticket.getTicketNo(), to);
            return true;
        } catch (Exception e) {
            log.warn("发送工单邮件失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送比对运行完成汇总邮件。
     */
    public boolean sendRunSummary(String to, Map<String, Object> model) {
        if (!enabled) {
            log.info("[邮件降级] 比对汇总需通知 {}（SMTP 未配置，已记录）", to);
            return false;
        }
        try {
            Template tpl = freemarker.getTemplate("run-summary.ftl");
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(tpl, model);
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
            helper.setTo(split(to));
            helper.setSubject("【元数据比对】定时巡检完成：" + model.get("taskName"));
            helper.setText(html, true);
            if (mailProperties.getUsername() != null && !mailProperties.getUsername().isBlank()) {
                helper.setFrom(mailProperties.getUsername());
            }
            mailSender.send(msg);
            log.info("汇总邮件已发送：{}", to);
            return true;
        } catch (Exception e) {
            log.warn("发送汇总邮件失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 异步发送运行汇总邮件（含新增工单清单）：不阻塞比对主流程。
     */
    public void sendRunSummaryAsync(String to, Map<String, Object> model) {
        mailExecutor.submit(() -> {
            try {
                boolean sent = sendRunSummary(to, model);
                if (!sent) {
                    log.debug("汇总邮件未发送（SMTP 未配置或发送失败）：{}", to);
                }
            } catch (Exception e) {
                log.warn("异步发送汇总邮件失败：{} -> {}", to, e.getMessage());
            }
        });
    }

    private String[] split(String to) {
        if (to == null || to.isBlank()) {
            return new String[0];
        }
        return to.split("[,;\\s]+");
    }
}
