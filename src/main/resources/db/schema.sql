-- ============================================================
-- 元数据自动化比对系统（一期）数据库建表脚本
-- 兼容 H2(MODE=MySQL) 与 MySQL 8.0
-- 类型选取原则：在 H2(MySQL 兼容) 与 MySQL 均合法
-- ============================================================

-- 任务配置（Cron、路径、收件人）
CREATE TABLE IF NOT EXISTS compare_task_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    task_name       VARCHAR(200) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    cron_expression VARCHAR(100) NOT NULL DEFAULT '0 0 2 * * ?',
    prod_ddl_path   VARCHAR(500),
    ddm_path        VARCHAR(500),
    soa_path        VARCHAR(500),
    file_spec_path  VARCHAR(500),
    recipients      VARCHAR(1000),
    last_run_time   DATETIME,
    PRIMARY KEY (id)
);

-- 任务执行记录（状态、统计）
CREATE TABLE IF NOT EXISTS compare_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    task_config_id  BIGINT,
    task_name       VARCHAR(200),
    status          VARCHAR(20)  NOT NULL,
    start_time      DATETIME,
    end_time        DATETIME,
    total_count     INT          DEFAULT 0,
    critical_count  INT          DEFAULT 0,
    warning_count   INT          DEFAULT 0,
    info_count      INT          DEFAULT 0,
    ticket_count    INT          DEFAULT 0,
    error_message   VARCHAR(1000),
    PRIMARY KEY (id)
);

-- 比对结果明细（规则、差异、追踪信息）
CREATE TABLE IF NOT EXISTS compare_result (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    task_id     BIGINT       NOT NULL,
    rule_name   VARCHAR(200),
    category    VARCHAR(50),
    severity    VARCHAR(20),
    table_name  VARCHAR(200),
    field_name  VARCHAR(200),
    message     TEXT,
    prod_value  VARCHAR(500),
    model_value VARCHAR(500),
    trace_info  TEXT,
    PRIMARY KEY (id)
);

-- 人工复核工单（状态流转）
CREATE TABLE IF NOT EXISTS review_ticket (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_no      VARCHAR(50)  NOT NULL,
    task_id        BIGINT,
    severity       VARCHAR(20),
    table_name     VARCHAR(200),
    field_name     VARCHAR(200),
    message        TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    assignee       VARCHAR(100),
    review_comment TEXT,
    notified       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time    DATETIME,
    update_time    DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no)
);

-- 操作日志
CREATE TABLE IF NOT EXISTS operation_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    op_type     VARCHAR(50),
    op_desc     VARCHAR(500),
    operator    VARCHAR(100),
    create_time DATETIME,
    PRIMARY KEY (id)
);

-- 分布式运行锁（多实例部署时保证同一任务单实例执行）
CREATE TABLE IF NOT EXISTS app_lock (
    lock_key    VARCHAR(100) NOT NULL,
    owner       VARCHAR(64)  NOT NULL,
    acquired_at DATETIME     NOT NULL,
    expires_at  DATETIME     NOT NULL,
    PRIMARY KEY (lock_key)
);

-- 查询索引（历史数据增长后避免全表扫描）
CREATE INDEX idx_result_task ON compare_result (task_id);
CREATE INDEX idx_task_config ON compare_task (task_config_id);
CREATE INDEX idx_ticket_task ON review_ticket (task_id);
CREATE INDEX idx_ticket_status ON review_ticket (status);
