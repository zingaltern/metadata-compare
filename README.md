# 元数据自动化比对系统

跨数据源元数据一致性比对工具：将生产库 DDL、DDM 模型、SOA 接口文档、文件规范四类元数据解析为统一中间格式，按表/字段关联匹配后，用可热加载的规则引擎自动比对，发现不一致与命名违规；严重（CRITICAL）问题自动生成人工复核工单并邮件通知。支持 cron 定时巡检、历史对比与网页上传。

## 功能特性

- 四类数据源解析：MySQL DDL、DDM 模型（XML/JSON）、SOA 接口（OpenAPI 3 / Swagger 2）、文件规范（Excel，中英文表头、多 sheet）
- 统一中间格式 + 可插拔解析器（新增格式只需实现 `MetadataParser` 并注册为 Bean）
- QLExpress 规则引擎：YAML 规则 + 消息模板，热加载（默认 60s 生效），单规则软超时
- 表名/字段名对照表：异构命名归一到逻辑名，老系统零改动（热加载）
- 结果分级（CRITICAL / WARNING / INFO）+ 严重问题自动建复核工单（状态流转）
- 源健康度报告：每次运行展示各数据源文件数/实体数/失败数，解析失败不再静默
- cron 定时调度（数据库配置、热更新）、历史对比（新增/解决/持续）、**异步汇总邮件**（每次运行一封、含新增工单清单，不阻塞比对；SMTP 未配置时优雅降级）
- 登录鉴权（表单 + Basic）、网页上传/删除元数据文件（路径穿越防护）
- 文件管理支持多选批量上传；删除的文件移入回收站（`data/input/.trash`，可找回），不物理删除
- 单页标签式控制台（触发比对 / 任务列表 / 工单复核），切换无整页刷新；任务与工单页每 30 秒自动刷新
- H2 开箱即跑，MySQL 8.0 生产 profile 一键切换

## 快速开始

前置：JDK 17、Maven 3.8+。

```bash
# ① 先进入项目目录（含 pom.xml 的目录），否则会报 "no POM in this directory"
cd /Users/zingaltern/WorkBuddy/2026-07-27-21-43-25/metadata-compare
# 如果是自己 clone 的仓库：git clone https://github.com/zingaltern/metadata-compare.git && cd metadata-compare

# ② 构建（含测试）；若本地仓库缺依赖报错，去掉 -o 联网构建：mvn package
mvn -o package          # 构建（含测试）；若本地仓库缺依赖报错，去掉 -o 联网构建：mvn package

# ③ 启动（8080 被占用时换一个空闲端口，如 8090）
java -jar target/metadata-compare.jar --server.port=8080   # 8080 被占用时换一个空闲端口，如 8090
```

启动后：

- 控制台：http://localhost:8080（默认账号 `admin / admin123`，生产务必修改）
- 触发一次比对：`curl -X POST -u admin:admin123 "http://localhost:8080/api/compare/trigger?configId=1"`（端口按你实际启动的端口改）
- H2 控制台：http://localhost:8080/h2-console（JDBC `jdbc:h2:mem:dqc`，用户 `sa`，空密码）

> 注意：若环境注入了 `SERVER__PORT` 环境变量，它会覆盖配置端口。可用 `env -u SERVER__PORT java -jar ...` 或 `--server.port=8080` 显式指定。

> **端口被占用**：启动报 `Port 8080 was already in use` 时，先 `lsof -iTCP:8080 -sTCP:LISTEN` 查看占用进程；与本系统无关的服务在跑时，直接换个端口启动即可：`java -jar target/metadata-compare.jar --server.port=8090`，后续访问与 curl 都改用 8090。

内置样例数据（`data/input/`）一次比对的预期结果：**total=6 / critical=2 / warning=4 / ticket=2**（详见下文“样例数据”）。

## 目录结构

```
metadata-compare/
├── src/main/java/com/dqc/compare/
│   ├── parser/        # 解析器路由 + DDL/XML/JSON/Swagger/Excel 实现
│   ├── rule/          # QLExpress 引擎、规则加载（热加载）、自定义函数
│   ├── service/       # ComparePipeline 比对编排、查询、分布式锁
│   ├── ticket/        # 复核工单
│   ├── notify/        # 邮件（Freemarker）
│   ├── scheduler/     # cron 调度
│   └── api/rest/      # REST 控制器
├── src/main/resources/
│   ├── application.yml / application-mysql.yml
│   ├── db/schema.sql
│   └── rules/compare-rules.yml / table-mappings.yml
├── rules/             # 运行时规则副本（热加载目标，首次启动从 classpath 拷贝）
├── data/input/        # 样例输入
└── src/test/java/     # 单元 + 端到端测试
```

## 比对流程

```
输入目录 → 解析器路由（SPI）→ 统一中间格式 StandardMetadata
  → 表名/字段名映射归一 → 按表关联 + 字段匹配
  → QLExpress 规则评估（字段级 / 表级）
  → 结果入库 + CRITICAL 建工单 + 邮件 → 更新任务最后执行时间
```

每次运行会记录 `compare_task` 与全部命中明细，并输出**源健康度**（各源文件数、实体数、失败文件），任何数据源解析为空或失败都会在报告与日志中显式告警。

## 规则配置

规则定义在 `rules/compare-rules.yml`（运行时热加载，约 60s 生效）：

```yaml
rules:
  - name: "中文字符检测"
    severity: CRITICAL
    category: VIOLATION
    action: CREATE_REVIEW_TICKET   # REPORT_ONLY | CREATE_REVIEW_TICKET
    scope: FIELD                   # FIELD（逐字段）| TABLE（整表）
    fieldNameFromSource: true      # true 时结果字段名取来源侧真实名（如中文字段名）
    message: "字段 [{chineseFieldName}] 名称包含中文字符（违规）"
    condition: "..."
```

内置规则（9 条）：中文字符检测、字段长度不一致、字段精度不一致、字段类型不兼容、模型缺失生产字段、生产缺失模型字段、必填字段在模型中变为可空、增量文件缺少时间戳、下发方式不一致（SOA 与文件规范两源交叉校验）。

**消息模板占位符**：`${tableName}`、`${fieldName}`、`${prodLength}`、`${modelLength}`、`${prodType}`、`${modelType}`、`${chineseFieldName}`、`${soaDeliveryType}`、`${specDeliveryType}` 等；未配置 message 时使用通用文案。

**注入变量**：`tableName`、`field`（四源首个非空字段）、`prodField/modelField/soaField/specField`（可空）、`fileSpec`、`soa`。

**自定义函数**：`hasChinese(s)`、`regexMatch(s, regex)`、`typeCompatible(a,b)`、`hasTimestampField(fields)`、`deliveryMismatch(a,b)`。

> ⚠️ QLExpress 会吞掉字符串字面量中的反斜杠，正则里不要写 `\` 转义；中文检测请用 `hasChinese()`。

## 表名/字段名对照

同一张表在不同来源命名不同（如生产 `T_ACCT`、DDM `ACCT_MODEL`、SOA `ACCT_INFO`）时，在 `rules/table-mappings.yml` 归一到逻辑表名/字段名（匹配不区分大小写、忽略反引号与空白，热加载）：

```yaml
mappings:
  - logicalName: ACCOUNT
    aliases: [T_ACCT, ACCT_MODEL, ACCT_INFO]
    fields:
      - logicalName: ACCT_ID
        aliases: [acctId]
```

未配置映射的表仍按原名精确匹配。多个文件出现重复逻辑表名时按文件排序取首个并告警。

## 样例数据与验收

`data/input/` 内置故意含违规项的样例（生产 DDL、DDM XML/JSON、SOA OpenAPI3/Swagger2、文件规范 Excel），一次比对预期：

| 级别 | 数量 | 命中 |
| --- | --- | --- |
| CRITICAL | 2 | 中文字段名 `CUSTOMER.客户名`；增量表 `AUDIT_LOG` 缺时间戳 |
| WARNING | 4 | `ORDER.ORDER_AMT`、`PRODUCT.PROD_NAME` 长度不一致；`PRODUCT.PROD_STATUS` 必填变可空；`ACCOUNT.CREATE_TIME` DDM 缺失（异名表归并后真实缺失） |
| 工单 | 2 | 两条 CRITICAL 各建一张 |

**反面信号**：`/` 返回 500、critical 数量异常暴增（中文字符规则误报）、critical>0 但 ticket=0、日志刷 `Skipped invalid entry`、某数据源 `entityCount=0`。

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/compare/trigger?configId=` | 手动触发（不传则触发全部启用任务） |
| GET | `/api/compare/tasks` | 任务列表 |
| GET | `/api/compare/tasks/{id}/results?page=&size=` | 结果明细（分页，默认 200/页，上限 500） |
| GET | `/api/compare/tasks/{id}/history-diff` | 与上一成功任务对比 |
| GET | `/api/tickets?status=&page=&size=` | 工单列表（分页） |
| GET | `/api/tickets/{id}` | 工单详情 |
| PUT | `/api/tickets/{id}/review` | 提交复核 `{status, reviewer, comment}` |
| PUT | `/api/config/tasks/{id}` | 更新任务配置（名称/启用/cron/路径/收件人） |
| POST/GET/DELETE | `/api/files/upload?source=&overwrite=`、`/api/files?source=` | 上传（可多选）/列出/删除（移入回收站）元数据文件 |

控制台页面：`/` 为单页标签式（控制台 / 任务列表 / 待复核工单，`/#tasks`、`/#tickets` 直达对应标签）；`/tasks.html`、`/tickets.html` 保留为兼容跳转。

## 配置项

| 配置键 | 默认 | 说明 |
| --- | --- | --- |
| `app.input.base-dir` | `./data/input` | 输入根目录 |
| `app.rules.path` / `hot-reload-seconds` | `./rules/compare-rules.yml` / 60 | 规则文件与热加载周期 |
| `app.table-mapping.path` / `hot-reload-seconds` | `./rules/table-mappings.yml` / 60 | 表名对照文件与热加载周期 |
| `app.scheduler.poll-seconds` | 30 | 调度轮询周期 |
| `app.rule.timeout-ms` | 3000 | 单规则软超时 |
| `app.ticket.no-prefix` | RT | 工单前缀 |
| `app.security.enabled` / `user` / `password` | true / admin / admin123 | 登录鉴权 |
| `app.base-url` | `http://localhost:8090` | 邮件中工单链接的域名前缀（部署后改实际域名/IP） |
| `app.notify.recipients` | 空 | 默认任务的邮件收件人（重启后自动恢复，可用 `APP_NOTIFY_RECIPIENTS` 注入） |
| `app.retention.task-days` / `ticket-days` | 365 / 0 | 任务结果保留天数 / 工单保留天数（0=永久保留） |

## 生产部署（MySQL）

1. DBA 执行 `src/main/resources/db/schema.sql` 建表（含分布式锁表 `app_lock` 与查询索引）
2. 修改 `application-mysql.yml` 的连接串、账号与 `app.security.password`（**mysql profile 下若仍为默认口令 admin123 将拒绝启动**）
3. 启动：`java -jar target/metadata-compare.jar --spring.profiles.active=mysql`

多实例部署：任务级分布式锁（`app_lock` 表）保证同一任务同一时刻只有一个实例执行；锁带 1 小时 TTL，实例崩溃后自动过期恢复（单次运行超过 1 小时的任务请调整 TTL 或控制调度频率）。

systemd 示例见 `项目详解.md`。

## 测试

```bash
mvn -o test
```

覆盖：解析器（DDL 复合主键 / Swagger2 转换 / Excel 中英文表头 / JSON-XML 模型）、规则函数、规则加载校验、表名映射，以及基于真实样例数据的端到端集成测试（验收签名 + 源健康度 + 下发方式不一致规则）。

## 已知限制（一期）

- 存量数据中的中文字符校验未实现（纯结构导入模式，无数据通道，属二期 AI/数据检查范围）
- DDL 仅覆盖 `CREATE TABLE` 常见语法（含复合主键）；`ALTER TABLE`、分区、表级外键等需扩展解析器
- DDM XML 仅支持属性式字段；元素式结构需新增解析器
- 字段对齐以精确匹配 + 对照表为主，语义级（同义词/类型宽化）为二期方向
- 单规则超时为软超时（QLExpress 不可中断），超时线程会继续跑完

## 后续方向

更多数据源解析（Kafka Schema / GraphQL / JDBC 反向采集）、规则命中趋势看板、工单对接企业 IM、结果导出 Excel、Docker + CI、多租户配置（Nacos）。
