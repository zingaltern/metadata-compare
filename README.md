# 元数据自动化比对系统

跨数据源元数据一致性比对工具：将**生产库 DDL、DDM 模型、SOA 接口、文件规范**四类元数据解析为统一中间格式，按表/字段关联后用可热加载的规则引擎自动比对；严重（CRITICAL）问题自动生成复核工单并邮件通知。支持 cron 定时巡检、历史对比、网页上传。

---

## 技术亮点

- **四源统一 + 解析器可插拔**：四类数据源都解析成 `StandardMetadata`（表+字段+扩展属性），新增格式只需实现 `MetadataParser` 接口并注册为 Spring Bean，不动比对主流程
- **规则引擎可热配置**：规则写在 `rules/compare-rules.yml`（QLExpress 表达式 + 消息模板），改完约 60 秒自动生效、无需重启；消息与字段归因全部由 YAML 驱动，新增/改名规则不用改 Java
- **老系统零改动**：`rules/table-mappings.yml` 表名/字段名对照表，把 `T_ACCT`/`ACCT_MODEL`/`ACCT_INFO` 这类异构命名归一到逻辑名，存量系统不用改名
- **自动 + 人工闭环**：规则命中分级（CRITICAL/WARNING/INFO）入库，严重问题自动建复核工单（确认/驳回/忽略），邮件汇总带工单直达链接
- **杜绝"静默丢源"**：每次运行输出源健康度（各源文件数/实体数/失败数/未识别文件），任何源为空或解析失败都显式告警
- **生产级可靠性**：任务级分布式锁（多实例不重复执行）、工单号并发安全、结果事务批量入库、数据保留策略自动清理
- **体验**：单页标签式控制台（30 秒自动刷新）、文件多选批量上传、删除进回收站可找回、H2 开箱即跑

---

## 快速开始（10 分钟上手）

前置：**JDK 17、Maven 3.8+**（`java -version`、`mvn -version` 确认）。

### 第 1 步：克隆并进入项目目录

```bash
git clone https://github.com/zingaltern/metadata-compare.git
cd metadata-compare
```

> 必须在含 `pom.xml` 的目录执行 Maven，否则报 `no POM in this directory`。

### 第 2 步：构建（含测试）

```bash
mvn -o package
```

- 首次构建若报依赖缺失，说明本地 Maven 仓库没有缓存，去掉 `-o` 联网构建：`mvn package`
- 成功标志：`BUILD SUCCESS`，且出现 `target/metadata-compare.jar`

### 第 3 步：启动

```bash
java -jar target/metadata-compare.jar --server.port=8080
```

- 启动日志出现 `Started MetadataCompareApplication` 即成功
- 若报 `Port 8080 was already in use`：先 `lsof -iTCP:8080 -sTCP:LISTEN` 看占用，换成 `--server.port=8090`
- 若环境有 `SERVER__PORT` 变量会覆盖端口，用 `env -u SERVER__PORT java -jar ...` 清除

### 第 4 步：登录并触发一次比对（验证安装）

1. 浏览器打开 http://localhost:8080（端口按实际启动的改）
2. 登录：`admin / admin123`
3. 点「触发一次比对」，或执行：

```bash
curl -X POST -u admin:admin123 "http://localhost:8080/api/compare/trigger?configId=1"
```

**预期结果（内置样例数据）：`total=6 / critical=2 / warning=4 / ticket=2`**。若数字一致，说明解析、比对、工单全链路正常。

### 第 5 步：接入你自己的元数据

控制台「文件管理」卡片：选择来源（生产 DDL / DDM 模型 / SOA 接口 / 文件规范）→ 多选上传你的文件 → 列表确认已落盘 → 再触发比对。

> 目录约定：`data/input/production/latest/`（.sql）、`ddm/`（.xml/.json）、`soa/`（.yaml/.json）、`file_spec/`（.xlsx）。不支持的格式会被跳过，并在报告的源健康度里提示。

### 第 6 步：查看任务与复核工单

- 顶部「任务列表」：每次运行的记录，点「查看结果」「历史对比」
- 顶部「待复核工单」：对严重问题提交复核（确认/驳回/忽略 + 处理人 + 意见）

### 10 分钟验收清单

- [ ] `mvn -o package` 成功产出 jar
- [ ] 启动后浏览器能打开并登录
- [ ] 触发比对返回 `6 / 2 / 4 / 2`
- [ ] 任务列表出现 1 条任务，工单列表出现 2 张 PENDING 工单
- [ ] （可选）配置邮件后收到一封汇总邮件

---

## 需要修改的配置（在哪里改）

| 配置项 | 在哪里改 | 怎么改 | 必须？ |
| --- | --- | --- | --- |
| 登录密码 | `src/main/resources/application.yml` → `app.security.password` | 改成强口令；或用环境变量 `APP_SECURITY_PASSWORD` 启动时注入 | **必须**（公网部署） |
| 端口 | `application.yml` → `server.port`，或启动参数 `--server.port=8090` | 被占用时换端口 | 可选 |
| SMTP 邮件 | 启动命令环境变量 `MAIL_HOST` / `MAIL_USER` / `MAIL_PASSWORD`；也可直接改 `application.yml` → `spring.mail.*` | 密码填**邮箱授权码**（非登录密码）；改 `application.yml` 后需重启 | 可选（建议） |
| 邮件收件人 | 启动命令环境变量 `APP_NOTIFY_RECIPIENTS`；或登录后 `PUT /api/config/tasks/1` 的 `recipients` 字段 | 不配则不发信，日志会 WARN 提示 | 可选（建议） |
| 邮件链接域名 | `application.yml` → `app.base-url` | 部署后改成实际域名/IP，否则邮件里的工单链接指向 localhost | 部署后必改 |
| 比对规则 | `rules/compare-rules.yml`（首次启动自动从 classpath 拷贝到运行目录） | 改完约 60 秒热加载生效，**无需重启**；结构见该文件注释 | 按需 |
| 表/字段映射 | `rules/table-mappings.yml` | 异构命名归一到逻辑名，热加载 | 按需 |
| 输入目录 | `application.yml` → `app.input.base-dir`（默认 `./data/input`） | 业务上直接用网页上传最省事，不必改目录 | 按需 |
| 保留策略 | `application.yml` → `app.retention.task-days` / `ticket-days` | 任务结果保留天数（默认 365）/ 工单保留天数（默认 0=永久） | 可选 |
| 数据库 | `application-mysql.yml` + 启动 `--spring.profiles.active=mysql` | 生产切 MySQL，见"生产部署" | 生产必须 |

**邮件配置完整示例**（每次运行发**一封**汇总邮件，含工单清单与直达链接）：

```bash
MAIL_HOST=smtp.qq.com MAIL_PORT=465 MAIL_USER=you@foxmail.com MAIL_PASSWORD=<授权码> \
APP_NOTIFY_RECIPIENTS=reviewer@example.com \
java -jar target/metadata-compare.jar
```

> ⚠️ 授权码属于个人凭证，**切勿提交到仓库**。未配置邮件时系统会打印明确 WARN（"SMTP 未配置"/"未配置收件人"），不会静默失败。

---

## 功能特性

- 四类数据源解析（MySQL DDL / DDM XML/JSON / SOA OpenAPI3+Swagger2 / 文件规范 Excel 中英文表头多 sheet），解析器 SPI 可插拔
- QLExpress 规则引擎：YAML 规则 + 消息模板，热加载，单规则软超时
- 表名/字段名对照表归一，老系统零改动
- 结果分级 + 严重问题自动建复核工单（状态流转）
- 源健康度报告、cron 定时调度（热更新）、历史对比（新增/解决/持续）
- 异步汇总邮件（不阻塞比对）、单页标签式控制台、文件多选上传/删除进回收站
- H2 开箱即跑，MySQL 8.0 生产 profile 一键切换

## 目录结构

```
├── src/main/java/com/dqc/compare/
│   ├── parser/        # 解析器路由 + DDL/XML/JSON/Swagger/Excel
│   ├── rule/          # QLExpress 引擎、规则热加载、自定义函数
│   ├── service/       # 比对编排、查询、分布式锁
│   ├── ticket/ notify/ scheduler/ api/rest/
├── src/main/resources/
│   ├── application.yml / application-mysql.yml
│   ├── db/schema.sql
│   └── rules/compare-rules.yml / table-mappings.yml
├── rules/             # 运行时规则副本（热加载目标）
├── data/input/        # 四类输入目录 + 样例数据
└── src/test/java/     # 单元 + 端到端测试
```

## 比对流程

```
输入目录 → 解析器路由（SPI）→ 统一中间格式
  → 表/字段名映射归一 → 按表关联 + 字段匹配
  → 规则评估（字段级/表级）→ 结果分级入库 + CRITICAL 建工单 + 异步邮件
```

每次运行记录 `compare_task` 与全部命中明细，并输出源健康度（各源文件数、实体数、失败文件），任何数据源为空或失败都会显式告警。

## 规则与映射

- **规则**：`rules/compare-rules.yml`，内置 9 条（中文字符检测、长度/精度/类型不一致、生产模型互缺字段、必填变可空、增量表缺时间戳、下发方式不一致）。自定义函数：`hasChinese` / `regexMatch` / `typeCompatible` / `hasTimestampField` / `deliveryMismatch`
- **映射**：`rules/table-mappings.yml`，把异名表/字段归一到逻辑名（如 `T_ACCT`/`ACCT_MODEL`/`ACCT_INFO` → `ACCOUNT`），未配置的按原名精确匹配
- ⚠️ QLExpress 会吞掉正则里的反斜杠，中文检测请用 `hasChinese()`，不要写 `\uXXXX` 转义

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/compare/trigger?configId=` | 手动触发（不传触发全部启用任务） |
| GET | `/api/compare/tasks?page=&size=` | 任务列表（默认 10 条/页） |
| GET | `/api/compare/tasks/{id}/results?page=&size=` | 结果明细（上限 500/页） |
| GET | `/api/compare/tasks/{id}/history-diff` | 与上一成功任务对比 |
| GET | `/api/tickets?status=&page=&size=` | 工单列表（默认 10 条/页） |
| PUT | `/api/tickets/{id}/review` | 提交复核 `{status, reviewer, comment}` |
| PUT | `/api/config/tasks/{id}` | 更新任务配置（cron/路径/收件人等） |
| POST/GET/DELETE | `/api/files/upload?source=&overwrite=`、`/api/files?source=` | 上传（可多选）/列出/删除（移入回收站） |

控制台页面：`/`（单页标签式，`/#tasks`、`/#tickets` 直达对应标签）；`/tasks.html`、`/tickets.html` 为兼容跳转。

## 生产部署（MySQL）

1. DBA 执行 `src/main/resources/db/schema.sql` 建表（含分布式锁表 `app_lock` 与查询索引）
2. 修改 `application-mysql.yml` 的连接串、账号与 `app.security.password`（默认口令会被拒绝启动）
3. 启动：`java -jar target/metadata-compare.jar --spring.profiles.active=mysql`

多实例部署：`app_lock` 分布式锁保证同一任务单实例执行（锁 TTL 1 小时，单次运行请远小于该时长）。systemd 示例见 `项目详解.md`。

## 测试

```bash
mvn -o test
```

覆盖：解析器（DDL 复合主键/Swagger2 转换/Excel 中英文表头/JSON-XML 模型）、规则函数、表名映射，以及样例数据端到端测试（验收签名 + 源健康度 + 下发方式规则）。

## 已知限制（一期）

- 存量数据中的中文字符校验未实现（纯结构导入模式，属二期范围）
- DDL 仅覆盖 `CREATE TABLE` 常见语法；DDM XML 仅支持属性式字段
- 字段对齐以精确匹配 + 对照表为主，语义级对齐为二期方向
- 单规则超时为软超时（QLExpress 不可中断）

## 后续方向

更多数据源解析、规则趋势看板、工单对接企业 IM、结果导出 Excel、Docker + CI、多租户配置。
