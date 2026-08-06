# 元数据自动化比对系统

跨数据源元数据一致性比对工具：将生产库 DDL、DDM 模型、SOA 接口、文件规范四类元数据解析为统一中间格式，按表/字段关联后用可热加载的规则引擎自动比对；严重（CRITICAL）问题自动生成复核工单并邮件通知，支持 cron 定时巡检、历史对比与网页上传。

## 快速开始

前置：JDK 17、Maven 3.8+。

```bash
# 克隆并进入项目目录（必须在含 pom.xml 的目录执行，否则报 "no POM in this directory"）
git clone https://github.com/zingaltern/metadata-compare.git
cd metadata-compare

# 构建（含测试）；若本地 Maven 仓库缺依赖报错，去掉 -o 联网构建：mvn package
mvn -o package

# 启动（默认 8080，被占用就换 --server.port=8090）
java -jar target/metadata-compare.jar --server.port=8080
```

启动后：

- 控制台：http://localhost:8080（默认账号 `admin / admin123`，**务必修改，见下表**）
- 触发比对：`curl -X POST -u admin:admin123 "http://localhost:8080/api/compare/trigger?configId=1"`
- H2 控制台：http://localhost:8080/h2-console（JDBC `jdbc:h2:mem:dqc`，用户 `sa`，空密码）

> 若环境注入了 `SERVER__PORT` 环境变量会覆盖端口，用 `env -u SERVER__PORT java -jar ...` 或命令行参数显式指定。

内置样例数据（`data/input/`）一次比对的预期结果：**total=6 / critical=2 / warning=4 / ticket=2**。

## 配置要求（必读）

| 配置项 | 默认 | 必须？ | 说明 |
| --- | --- | --- | --- |
| 登录账号 `app.security.user/password` | admin / admin123 | **必须改** | 公网部署不改等于裸奔；mysql profile 下用默认口令会**拒绝启动** |
| 端口 `server.port` | 8080 | 可选 | 被占用时换端口启动 |
| 邮件 `MAIL_HOST / MAIL_USER / MAIL_PASSWORD` | 空 | 可选（建议） | 不配则不发信（日志 WARN 提示）；密码填**邮箱授权码**而非登录密码 |
| 收件人 `APP_NOTIFY_RECIPIENTS` | 空 | 可选（建议） | 不配则不发信；也可登录后 `PUT /api/config/tasks/1` 设置 `recipients` |
| 邮件链接域名 `app.base-url` | http://localhost:8090 | 部署后改 | 邮件里"查看工单"链接的前缀 |
| 数据库 `spring.datasource.*` | H2 内存 | 演示默认 | 生产切 MySQL（见"生产部署"） |
| 输入目录 `app.input.base-dir` | ./data/input | 使用前确认 | 下面四个子目录放元数据文件 |
| 规则/表映射 `rules/*.yml` | 内置样例 | 按需修改 | 修改后约 60s 热加载生效 |
| 保留策略 `app.retention.task-days / ticket-days` | 365 / 0 | 可选 | 任务结果保留天数 / 工单保留天数（0=永久保留） |

### 邮件配置示例

系统在每次运行结束后发**一封汇总邮件**（含新增工单清单与直达链接，不逐工单发信）。启动时注入环境变量即可：

```bash
MAIL_HOST=smtp.qq.com MAIL_PORT=465 MAIL_USER=you@foxmail.com MAIL_PASSWORD=<授权码> \
APP_NOTIFY_RECIPIENTS=reviewer@example.com \
java -jar target/metadata-compare.jar
```

> 未配置时系统会打印明确 WARN（"SMTP 未配置"/"未配置收件人"），不会静默失败。**授权码属于个人凭证，切勿提交到仓库。**

## 功能特性

- 四类数据源解析：MySQL DDL、DDM 模型（XML/JSON）、SOA 接口（OpenAPI 3 / Swagger 2）、文件规范（Excel 中英文表头/多 sheet），解析器可插拔
- QLExpress 规则引擎：YAML 规则 + 消息模板，热加载，单规则软超时
- 表名/字段名对照表：异构命名归一到逻辑名，老系统零改动
- 结果分级（CRITICAL/WARNING/INFO）+ 严重问题自动建复核工单（状态流转）
- 源健康度报告：每次运行展示各源文件/实体/失败数，解析失败与未识别文件显式告警
- cron 定时调度（热更新）、历史对比（新增/解决/持续）、异步汇总邮件（不阻塞比对）
- 单页标签式控制台（触发比对/任务列表/工单复核），30 秒自动刷新；文件多选批量上传、删除进回收站
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
  → 规则评估（字段级/表级）→ 结果分级入库 + CRITICAL 建工单 + 邮件
```

## 规则与映射

- 规则：`rules/compare-rules.yml`，内置 9 条（中文字符检测、长度/精度/类型不一致、生产模型互缺字段、必填变可空、增量表缺时间戳、下发方式不一致）。结构见文件注释；自定义函数：`hasChinese` / `regexMatch` / `typeCompatible` / `hasTimestampField` / `deliveryMismatch`
- 映射：`rules/table-mappings.yml`，把异名表/字段归一到逻辑名（如 `T_ACCT`/`ACCT_MODEL`/`ACCT_INFO` → `ACCOUNT`），未配置的按原名精确匹配
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
