package com.dqc.compare.service;

import com.dqc.compare.api.rest.TaskRunningException;
import com.dqc.compare.config.AppProperties;
import com.dqc.compare.config.TableMappingLoader;
import com.dqc.compare.dto.CompareReport;
import com.dqc.compare.dto.SourceHealth;
import com.dqc.compare.entity.CompareResult;
import com.dqc.compare.entity.CompareTask;
import com.dqc.compare.entity.CompareTaskConfig;
import com.dqc.compare.entity.OperationLog;
import com.dqc.compare.entity.ReviewTicket;
import com.dqc.compare.mapper.CompareResultMapper;
import com.dqc.compare.mapper.CompareTaskConfigMapper;
import com.dqc.compare.mapper.CompareTaskMapper;
import com.dqc.compare.mapper.OperationLogMapper;
import com.dqc.compare.model.FieldMeta;
import com.dqc.compare.model.RuleAction;
import com.dqc.compare.model.Severity;
import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.notify.MailService;
import com.dqc.compare.parser.ParserRouter;
import com.dqc.compare.parser.ParseDirectoryResult;
import com.dqc.compare.rule.RuleDef;
import com.dqc.compare.rule.RuleEngine;
import com.dqc.compare.rule.RuleEvalResult;
import com.dqc.compare.rule.RuleLoader;
import com.dqc.compare.ticket.ReviewTicketService;
import com.dqc.compare.ticket.TicketRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 比对流水线（对应文档 3.1 处理层 / 3.2 比对流程）。
 * <pre>
 *   文件目录 → 解析器路由(SPI) → 统一中间格式 → 按表关联匹配（经表名映射归一到逻辑表名）
 *     → QLExpress 规则引擎 → 结果分级 → 严重问题自动创建复核工单 → 邮件通知 → 持久化
 * </pre>
 *
 * <p>一致性保证：评估阶段结果/工单先在内存累积，全部评估完成后在事务内批量入库；
 * 任一环节异常则整次比对回滚（不残留半成品结果），仅保留 task=FAILED 的执行记录。</p>
 */
@Service
public class ComparePipeline {

    private static final Logger log = LoggerFactory.getLogger(ComparePipeline.class);

    private static final Set<String> FIELD_VARS = Set.of("field", "prodField", "modelField", "soaField", "specField");
    private static final Pattern[] FIELD_VAR_PATTERNS = FIELD_VARS.stream()
            .map(v -> Pattern.compile("\\b" + v + "\\b"))
            .toArray(Pattern[]::new);

    private final ParserRouter parserRouter;
    private final RuleEngine ruleEngine;
    private final RuleLoader ruleLoader;
    private final CompareTaskMapper taskMapper;
    private final CompareResultMapper resultMapper;
    private final CompareTaskConfigMapper configMapper;
    private final ReviewTicketService ticketService;
    private final OperationLogMapper operationLogMapper;
    private final AppProperties appProperties;
    private final MailService mailService;
    private final TableMappingLoader tableMappingLoader;
    private final DistributedLockService distributedLockService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TransactionTemplate txTemplate;
    /** 实例标识：与任务号一起构成锁 owner，保证多实例部署下锁互斥。 */
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public ComparePipeline(ParserRouter parserRouter, RuleEngine ruleEngine, RuleLoader ruleLoader,
                           CompareTaskMapper taskMapper, CompareResultMapper resultMapper,
                           CompareTaskConfigMapper configMapper, ReviewTicketService ticketService,
                           OperationLogMapper operationLogMapper, AppProperties appProperties,
                           MailService mailService, TableMappingLoader tableMappingLoader,
                           DistributedLockService distributedLockService,
                           PlatformTransactionManager txManager) {
        this.parserRouter = parserRouter;
        this.ruleEngine = ruleEngine;
        this.ruleLoader = ruleLoader;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.configMapper = configMapper;
        this.ticketService = ticketService;
        this.operationLogMapper = operationLogMapper;
        this.appProperties = appProperties;
        this.mailService = mailService;
        this.tableMappingLoader = tableMappingLoader;
        this.distributedLockService = distributedLockService;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public CompareReport run(Long configId) {
        CompareTaskConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new IllegalArgumentException("任务配置不存在: " + configId);
        }
        return run(config);
    }

    public CompareReport run(CompareTaskConfig config) {
        Long cfgId = config.getId();
        String lockKey = "compare:run:" + cfgId;
        String owner = instanceId + ":" + cfgId;
        // 数据库级分布式锁：多实例部署时同一任务也只允许一个实例执行，防止结果/工单重复入库
        if (!distributedLockService.tryAcquire(lockKey, owner, java.time.Duration.ofHours(1))) {
            throw new TaskRunningException("任务[" + config.getTaskName() + "]正在运行，请稍后重试");
        }
        long start = System.currentTimeMillis();
        CompareReport report = new CompareReport();
        report.setTaskName(config.getTaskName());

        CompareTask task = new CompareTask();
        task.setTaskConfigId(config.getId());
        task.setTaskName(config.getTaskName());
        task.setStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        taskMapper.insert(task);
        report.setTaskId(task.getId());

        // 评估阶段先累积到内存，成功后再统一入库（失败不残留）
        List<CompareResult> resultBuffer = new ArrayList<>();
        List<TicketRequest> ticketBuffer = new ArrayList<>();

        try {
            // 1) 解析各数据源
            Map<SourceType, ParseDirectoryResult> parsedDetailed = parseAllDetailed(config);
            Map<SourceType, List<StandardMetadata>> parsed = new LinkedHashMap<>();
            for (Map.Entry<SourceType, ParseDirectoryResult> e : parsedDetailed.entrySet()) {
                parsed.put(e.getKey(), e.getValue().getEntities());
            }
            report.setSourceHealth(buildSourceHealth(config, parsedDetailed));
            report.setSourceHealthSummary(buildSourceHealthSummary(report.getSourceHealth()));
            warnUnhealthySources(report.getSourceHealth());

            // 2) 建索引（按归一化表名；经表名映射归一到逻辑表名，使异构命名也能关联）
            Map<String, StandardMetadata> prodMap = indexByTable(parsed.get(SourceType.PRODUCTION_DDL));
            Map<String, StandardMetadata> ddmMap = indexByTable(parsed.get(SourceType.DDM_MODEL));
            Map<String, StandardMetadata> soaMap = indexByTable(parsed.get(SourceType.SOA_API));
            Map<String, StandardMetadata> specMap = indexByTable(parsed.get(SourceType.FILE_SPEC));

            // 3) 按表关联匹配 + 规则评估
            Set<String> allTables = new LinkedHashSet<>();
            allTables.addAll(prodMap.keySet());
            allTables.addAll(ddmMap.keySet());
            allTables.addAll(soaMap.keySet());
            allTables.addAll(specMap.keySet());

            List<RuleDef> fieldRules = new ArrayList<>();
            List<RuleDef> tableRules = new ArrayList<>();
            for (RuleDef r : ruleLoader.getRules()) {
                (isFieldLevel(r) ? fieldRules : tableRules).add(r);
            }
            log.info("规则加载：字段级 {} 条，表级 {} 条", fieldRules.size(), tableRules.size());

            for (String normName : allTables) {
                StandardMetadata prod = prodMap.get(normName);
                StandardMetadata ddm = ddmMap.get(normName);
                StandardMetadata soa = soaMap.get(normName);
                StandardMetadata spec = specMap.get(normName);
                String tableName = pickTableName(prod, ddm, soa, spec);

                // 表级规则（如：增量文件缺少时间戳）
                for (RuleDef rule : tableRules) {
                    Map<String, Object> ctx = new LinkedHashMap<>();
                    ctx.put("tableName", tableName);
                    ctx.put("soa", soa);
                    ctx.put("fileSpec", spec);
                    evaluateAndCollect(rule, tableName, ctx, config, report, resultBuffer, ticketBuffer,
                            null, null, null, null, null);
                }

                // 字段级规则：按"逻辑字段名"逐一评估（经字段映射归一到逻辑字段名，使异构字段名也能关联）
                Map<String, FieldMeta> prodFields = indexFieldsByLogical(prod, normName);
                Map<String, FieldMeta> ddmFields = indexFieldsByLogical(ddm, normName);
                Map<String, FieldMeta> soaFields = indexFieldsByLogical(soa, normName);
                Map<String, FieldMeta> specFields = indexFieldsByLogical(spec, normName);
                Set<String> fieldNames = new LinkedHashSet<>();
                fieldNames.addAll(prodFields.keySet());
                fieldNames.addAll(ddmFields.keySet());
                fieldNames.addAll(soaFields.keySet());
                fieldNames.addAll(specFields.keySet());
                for (String fn : fieldNames) {
                    Map<String, Object> ctx = new LinkedHashMap<>();
                    FieldMeta prodF = prodFields.get(fn);
                    FieldMeta ddmF = ddmFields.get(fn);
                    FieldMeta soaF = soaFields.get(fn);
                    FieldMeta specF = specFields.get(fn);
                    ctx.put("tableName", tableName);
                    ctx.put("field", firstNonNull(prodF, ddmF, soaF, specF));
                    ctx.put("prodField", prodF);
                    ctx.put("modelField", ddmF);
                    ctx.put("soaField", soaF);
                    ctx.put("specField", specF);
                    ctx.put("soa", soa);
                    ctx.put("fileSpec", spec);
                    for (RuleDef rule : fieldRules) {
                        evaluateAndCollect(rule, tableName, ctx, config, report, resultBuffer, ticketBuffer, fn, prodF, ddmF, soaF, specF);
                    }
                }
            }

            // 4) 统一入库：结果批量写入（单事务），工单逐条创建并通知（邮件不占事务连接）
            persistResults(report.getTaskId(), resultBuffer);
            for (TicketRequest p : ticketBuffer) {
                try {
                    ReviewTicket t = ticketService.createAndNotify(report.getTaskId(), p.rule(), p.tableName(),
                            p.fieldName(), p.message(), p.recipients());
                    report.getTickets().add(t);
                } catch (Exception e) {
                    log.error("创建复核工单失败（规则={}）：{}", p.rule().getName(), e.getMessage());
                }
            }

            // 5) 运行汇总邮件
            trySendSummary(config, report);

            // 6) 更新配置最后执行时间
            config.setLastRunTime(LocalDateTime.now());
            configMapper.updateById(config);

            task.setStatus("SUCCESS");
            task.setErrorMessage(null);
        } catch (Exception e) {
            log.error("比对执行失败：{}", e.getMessage(), e);
            task.setStatus("FAILED");
            task.setErrorMessage(truncate(e.getMessage()));
            report.setStatus("FAILED");
            report.setErrorMessage(e.getMessage());
        } finally {
            distributedLockService.release(lockKey, owner);
            task.setTotalCount(report.getTotalCount());
            task.setCriticalCount(report.getCriticalCount());
            task.setWarningCount(report.getWarningCount());
            task.setInfoCount(report.getInfoCount());
            task.setTicketCount(report.getTicketCount());
            task.setEndTime(LocalDateTime.now());
            taskMapper.updateById(task);
            report.setDurationMs(System.currentTimeMillis() - start);
            report.setStatus(report.getStatus() == null ? task.getStatus() : report.getStatus());
            logOperation(config, report);
        }
        return report;
    }

    /** 结果批量入库（单事务原子写入）。 */
    private void persistResults(Long taskId, List<CompareResult> results) {
        if (results.isEmpty()) {
            return;
        }
        for (CompareResult cr : results) {
            cr.setTaskId(taskId);
        }
        txTemplate.executeWithoutResult(status -> resultMapper.insertBatch(results));
    }

    // ---------------- 内部方法 ----------------

    private void evaluateAndCollect(RuleDef rule, String tableName, Map<String, Object> ctx,
                                     CompareTaskConfig config, CompareReport report,
                                     List<CompareResult> resultBuffer, List<TicketRequest> ticketBuffer,
                                     String logicalFieldName,
                                     FieldMeta prodF, FieldMeta ddmF, FieldMeta soaF, FieldMeta specF) {
        RuleEvalResult r = ruleEngine.evaluate(rule, ctx, appProperties.getRule().getTimeoutMs());
        if (!r.isMatched()) {
            return;
        }
        FieldMeta field = (FieldMeta) ctx.get("field");
        FieldMeta prod = (FieldMeta) ctx.get("prodField");
        FieldMeta model = (FieldMeta) ctx.get("modelField");
        // 字段名展示：规则声明 fieldNameFromSource 时取来源侧真实字段名（如中文字段名），
        // 否则用逻辑字段名（归一、跨源一致）。由 YAML 规则配置驱动，不再按规则名硬编码。
        String fieldName = rule.isFieldNameFromSource()
                ? findSourceFieldName(prodF, ddmF, soaF, specF)
                : (logicalFieldName != null ? logicalFieldName
                    : (field != null ? field.getFieldName()
                        : (prod != null ? prod.getFieldName() : (model != null ? model.getFieldName() : null))));

        CompareResult cr = new CompareResult();
        cr.setTaskId(report.getTaskId());
        cr.setRuleName(rule.getName());
        cr.setCategory(rule.getCategory() == null ? null : rule.getCategory().name());
        cr.setSeverity(rule.getSeverity() == null ? "INFO" : rule.getSeverity().name());
        cr.setTableName(tableName);
        cr.setFieldName(fieldName);
        cr.setMessage(resolveMessage(rule, tableName, ctx, logicalFieldName, prodF, ddmF, soaF, specF));
        cr.setProdValue(summarize(prod));
        cr.setModelValue(summarize(model));
        cr.setTraceInfo(toTraceJson(rule, tableName, ctx, r));
        resultBuffer.add(cr);
        report.getResults().add(cr);

        // 计数
        report.setTotalCount(report.getTotalCount() + 1);
        Severity sev = rule.getSeverity();
        if (sev == null) {
            report.setInfoCount(report.getInfoCount() + 1);
        } else if (sev == Severity.CRITICAL) {
            report.setCriticalCount(report.getCriticalCount() + 1);
        } else if (sev == Severity.WARNING) {
            report.setWarningCount(report.getWarningCount() + 1);
        } else {
            report.setInfoCount(report.getInfoCount() + 1);
        }

        // 严重问题 → 记录待创建工单（在统一入库阶段落库并通知）
        if (rule.getAction() == RuleAction.CREATE_REVIEW_TICKET) {
            ticketBuffer.add(new TicketRequest(rule, tableName, fieldName, cr.getMessage(), config.getRecipients()));
            report.setTicketCount(report.getTicketCount() + 1);
        }
    }

    /**
     * 消息渲染：规则在 YAML 中声明 message 模板（${tableName}/${fieldName}/${prodLength}…），
     * 未声明时使用通用文案。不再按规则名硬编码消息，新增/改名规则无需改 Java。
     */
    private String resolveMessage(RuleDef rule, String tableName, Map<String, Object> ctx,
                                  String logicalFieldName,
                                  FieldMeta prod, FieldMeta model, FieldMeta soa, FieldMeta spec) {
        FieldMeta field = (FieldMeta) ctx.get("field");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("ruleName", rule.getName());
        vars.put("severity", rule.getSeverity() == null ? "INFO" : rule.getSeverity().name());
        vars.put("category", rule.getCategory() == null ? "" : rule.getCategory().name());
        vars.put("tableName", tableName == null ? "" : tableName);
        vars.put("fieldName", fieldNameOf(field, prod, model));
        vars.put("logicalFieldName", logicalFieldName == null ? "" : logicalFieldName);
        vars.put("chineseFieldName", findChineseFieldName(prod, model, soa, spec) == null
                ? "" : findChineseFieldName(prod, model, soa, spec));
        vars.put("prodFieldName", nvl(prod == null ? null : prod.getFieldName()));
        vars.put("modelFieldName", nvl(model == null ? null : model.getFieldName()));
        vars.put("soaFieldName", nvl(soa == null ? null : soa.getFieldName()));
        vars.put("specFieldName", nvl(spec == null ? null : spec.getFieldName()));
        vars.put("prodLength", prod == null || prod.getLength() == null ? "" : prod.getLength());
        vars.put("modelLength", model == null || model.getLength() == null ? "" : model.getLength());
        vars.put("prodPrecision", prod == null || prod.getPrecision() == null ? "" : prod.getPrecision());
        vars.put("modelPrecision", model == null || model.getPrecision() == null ? "" : model.getPrecision());
        vars.put("prodType", nvl(prod == null ? null : prod.getDataType()));
        vars.put("modelType", nvl(model == null ? null : model.getDataType()));
        vars.put("prodNullable", prod == null || prod.getNullable() == null ? "" : prod.getNullable());
        vars.put("modelNullable", model == null || model.getNullable() == null ? "" : model.getNullable());
        vars.put("prodValue", nvl(summarize(prod)));
        vars.put("modelValue", nvl(summarize(model)));
        StandardMetadata specMeta = (StandardMetadata) ctx.get("fileSpec");
        StandardMetadata soaMeta = (StandardMetadata) ctx.get("soa");
        vars.put("specDeliveryType", nvl(specMeta == null ? null : specMeta.getDeliveryType()));
        vars.put("soaDeliveryType", nvl(soaMeta == null ? null : soaMeta.getDeliveryType()));

        String template = rule.getMessage();
        if (template == null || template.isBlank()) {
            template = "{ruleName} 命中（表={tableName}，字段={fieldName}）";
        }
        return resolveTemplate(template, vars);
    }

    private String resolveTemplate(String template, Map<String, Object> vars) {
        Matcher m = Pattern.compile("\\$?\\{([A-Za-z0-9_.]+)}").matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Object v = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String fieldNameOf(FieldMeta field, FieldMeta prod, FieldMeta model) {
        return nvl(field != null ? field.getFieldName()
                : (prod != null ? prod.getFieldName() : (model != null ? model.getFieldName() : null)));
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String toTraceJson(RuleDef rule, String tableName, Map<String, Object> ctx, RuleEvalResult r) {
        try {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("rule", rule.getName());
            trace.put("condition", rule.getCondition());
            trace.put("severity", rule.getSeverity());
            trace.put("tableName", tableName);
            trace.put("field", summarize((FieldMeta) ctx.get("field")));
            trace.put("prodField", summarize((FieldMeta) ctx.get("prodField")));
            trace.put("modelField", summarize((FieldMeta) ctx.get("modelField")));
            StandardMetadata spec = (StandardMetadata) ctx.get("fileSpec");
            trace.put("deliveryType", spec == null ? null : spec.getDeliveryType());
            trace.put("matched", true);
            trace.put("elapsedMs", r.getElapsedMs());
            trace.put("error", r.getError());
            return objectMapper.writeValueAsString(trace);
        } catch (Exception e) {
            return "{\"error\":\"trace serialize failed\"}";
        }
    }

    private String summarize(FieldMeta f) {
        if (f == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(f.getFieldName() == null ? "" : f.getFieldName());
        sb.append(':').append(f.getDataType() == null ? "?" : f.getDataType());
        if (f.getLength() != null) {
            sb.append('(').append(f.getLength());
            if (f.getPrecision() != null) {
                sb.append(',').append(f.getPrecision());
            }
            sb.append(')');
        }
        sb.append(f.getNullable() == null ? "" : (f.getNullable() ? " nullable" : " not-null"));
        return sb.toString();
    }

    private void trySendSummary(CompareTaskConfig config, CompareReport report) {
        if (config.getRecipients() == null || config.getRecipients().isBlank()) {
            return;
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("taskName", report.getTaskName());
        model.put("taskId", report.getTaskId());
        model.put("totalCount", report.getTotalCount());
        model.put("criticalCount", report.getCriticalCount());
        model.put("warningCount", report.getWarningCount());
        model.put("ticketCount", report.getTicketCount());
        model.put("durationMs", report.getDurationMs());
        model.put("sourceHealthSummary", report.getSourceHealthSummary());
        try {
            mailService.sendRunSummary(config.getRecipients(), model);
        } catch (Exception e) {
            log.debug("汇总邮件发送跳过：{}", e.getMessage());
        }
    }

    private void logOperation(CompareTaskConfig config, CompareReport report) {
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setOpType("COMPARE_RUN");
            logEntry.setOpDesc("任务[" + report.getTaskName() + "] 状态=" + report.getStatus()
                    + " 问题数=" + report.getTotalCount() + " 工单=" + report.getTicketCount());
            logEntry.setOperator("system");
            logEntry.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(logEntry);
        } catch (Exception ignored) {
            // 日志表非关键路径
        }
    }

    private Map<SourceType, ParseDirectoryResult> parseAllDetailed(CompareTaskConfig config) {
        Map<SourceType, ParseDirectoryResult> map = new LinkedHashMap<>();
        map.put(SourceType.PRODUCTION_DDL, parseDirDetailed(config.getProdDdlPath()));
        map.put(SourceType.DDM_MODEL, parseDirDetailed(config.getDdmPath()));
        map.put(SourceType.SOA_API, parseDirDetailed(config.getSoaPath()));
        map.put(SourceType.FILE_SPEC, parseDirDetailed(config.getFileSpecPath()));
        return map;
    }

    private ParseDirectoryResult parseDirDetailed(String dir) {
        if (dir == null || dir.isBlank()) {
            return new ParseDirectoryResult(List.of(), List.of(), List.of(), 0);
        }
        try {
            Path p = Paths.get(dir);
            return parserRouter.parseDirectoryDetailed(p);
        } catch (Exception e) {
            log.warn("解析目录失败（跳过）：{} -> {}", dir, e.getMessage());
            return new ParseDirectoryResult(List.of(), List.of(), List.of(), 0);
        }
    }

    /** 构建各数据源的解析健康度（暴露"某源为空/有失败文件"信号）。 */
    private List<SourceHealth> buildSourceHealth(CompareTaskConfig config,
                                                  Map<SourceType, ParseDirectoryResult> parsed) {
        List<SourceHealth> health = new ArrayList<>();
        for (Map.Entry<SourceType, ParseDirectoryResult> e : parsed.entrySet()) {
            SourceType st = e.getKey();
            ParseDirectoryResult r = e.getValue();
            SourceHealth h = new SourceHealth();
            h.setSourceType(st);
            h.setFileCount(r.getTotalFiles());
            h.setParsedFileCount(Math.max(0, r.getTotalFiles()
                    - r.getFailedFiles().size() - r.getEmptyFiles().size()));
            h.setEntityCount(r.getEntities().size());
            h.setFieldCount(r.getEntities().stream().mapToInt(m -> m.getFields() == null ? 0 : m.getFields().size()).sum());
            h.setEmptyFileCount(r.getEmptyFiles().size());
            h.setFailedFileCount(r.getFailedFiles().size());
            List<String> problems = new ArrayList<>();
            problems.addAll(r.getFailedFiles());
            problems.addAll(r.getEmptyFiles());
            h.setFailedFiles(problems);
            if (r.getTotalFiles() == 0) {
                h.setWarning("输入目录为空或不存在，该数据源未参与比对");
            } else if (h.getEntityCount() == 0) {
                h.setWarning("该数据源未解析出任何表/实体，请检查输入文件格式或解析器");
            } else if (h.getFailedFileCount() > 0 || h.getEmptyFileCount() > 0) {
                List<String> names = problems.stream()
                        .map(p -> {
                            int i = p.lastIndexOf('/');
                            return i >= 0 ? p.substring(i + 1) : p;
                        })
                        .limit(5)
                        .toList();
                String suffix = names.isEmpty() ? "" : "：" + String.join("、", names)
                        + (problems.size() > 5 ? " 等" + problems.size() + " 个" : "");
                h.setWarning("有 " + (h.getFailedFileCount() + h.getEmptyFileCount())
                        + " 个文件未识别/未解析出元数据，已跳过（可能传错目录或格式不支持，结果可能不完整）" + suffix);
            }
            health.add(h);
        }
        return health;
    }

    private String buildSourceHealthSummary(List<SourceHealth> health) {
        StringBuilder sb = new StringBuilder();
        for (SourceHealth h : health) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(h.getSourceType()).append('(').append(h.getEntityCount())
                    .append("实体/").append(h.getFailedFileCount()).append("失败/")
                    .append(h.getEmptyFileCount()).append("未识别)");
            if (!h.isHealthy()) {
                sb.append("⚠");
            }
        }
        return sb.toString();
    }

    private void warnUnhealthySources(List<SourceHealth> health) {
        for (SourceHealth h : health) {
            if (!h.isHealthy()) {
                log.warn("数据源健康检查：{} {}", h.getSourceType(), h.getWarning());
            }
        }
    }

    /**
     * 建索引：用"经表名映射归一到逻辑表名"后的归一化名称作 key，
     * 使生产 T_ACCT / DDM ACCT_MODEL / SOA ACCT_INFO 这类异名表能落到同一 key 正确关联。
     */
    private Map<String, StandardMetadata> indexByTable(List<StandardMetadata> list) {
        Map<String, StandardMetadata> map = new LinkedHashMap<>();
        if (list == null) {
            return map;
        }
        for (StandardMetadata m : list) {
            if (m.getTableName() == null) {
                continue;
            }
            String logical = tableMappingLoader.resolveLogicalName(m.getTableName());
            String key = normalizeTable(logical);
            if (map.containsKey(key)) {
                log.warn("发现重复逻辑表名（按文件排序取首个，后者被忽略）：{} 来源={}",
                        key, m.getSourceType());
                continue;
            }
            map.put(key, m);
        }
        return map;
    }

    /**
     * 把某来源的字段按"逻辑字段名"建索引：经字段映射把异构字段名归一到同一逻辑名。
     * 归一时取该逻辑名下首个出现的字段（多源同名字段以首源为准）。
     */
    private Map<String, FieldMeta> indexFieldsByLogical(StandardMetadata m, String logicalTable) {
        Map<String, FieldMeta> map = new LinkedHashMap<>();
        if (m == null || m.getFields() == null) {
            return map;
        }
        for (FieldMeta f : m.getFields()) {
            if (f.getFieldName() == null) {
                continue;
            }
            String logical = tableMappingLoader.resolveFieldName(logicalTable, f.getFieldName());
            String key = logical != null ? logical : normalizeField(f.getFieldName());
            map.putIfAbsent(key, f);
        }
        return map;
    }

    /** 在四个来源的字段中找出首个名称含中文者（供消息模板 ${chineseFieldName} 使用）。 */
    private String findChineseFieldName(FieldMeta prod, FieldMeta ddm, FieldMeta soa, FieldMeta spec) {
        for (FieldMeta f : new FieldMeta[]{prod, ddm, soa, spec}) {
            if (f != null && f.getFieldName() != null && hasChinese(f.getFieldName())) {
                return f.getFieldName();
            }
        }
        return null;
    }

    /** 取来源侧真实字段名：优先含中文者，否则取首个非空来源字段。 */
    private String findSourceFieldName(FieldMeta prod, FieldMeta ddm, FieldMeta soa, FieldMeta spec) {
        String chinese = findChineseFieldName(prod, ddm, soa, spec);
        if (chinese != null) {
            return chinese;
        }
        for (FieldMeta f : new FieldMeta[]{prod, ddm, soa, spec}) {
            if (f != null && f.getFieldName() != null) {
                return f.getFieldName();
            }
        }
        return null;
    }

    /** 码点级中文判定（与 HasChineseFunction 一致，避免 QLExpress 反斜杠陷阱）。 */
    private boolean hasChinese(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private FieldMeta firstNonNull(FieldMeta... fs) {
        for (FieldMeta f : fs) {
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    /**
     * 结果中展示的表名：若命中映射则返回逻辑表名（便于识别"同一张表"），否则返回原始表名。
     */
    private String pickTableName(StandardMetadata... metas) {
        for (StandardMetadata m : metas) {
            if (m != null && m.getTableName() != null) {
                String logical = tableMappingLoader.resolveLogicalName(m.getTableName());
                if (logical != null && !logical.equalsIgnoreCase(m.getTableName())) {
                    return logical;
                }
            }
        }
        for (StandardMetadata m : metas) {
            if (m != null && m.getTableName() != null) {
                return m.getTableName();
            }
        }
        return null;
    }

    private boolean isFieldLevel(RuleDef rule) {
        String scope = rule.getScope();
        if (scope != null && !scope.isBlank()) {
            return !"TABLE".equalsIgnoreCase(scope.trim());
        }
        // 兜底：按 condition 是否引用字段级变量嗅探（历史规则无 scope 时兼容）
        String cond = rule.getCondition();
        if (cond == null) {
            return true;
        }
        for (Pattern p : FIELD_VAR_PATTERNS) {
            if (p.matcher(cond).find()) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTable(String s) {
        return s == null ? "" : s.replace("`", "").trim().toUpperCase();
    }

    private String normalizeField(String s) {
        return s == null ? "" : s.replace("`", "").trim().toUpperCase();
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
