# API 接口测试报告 — F2 临期阈值回归（2026-09-29）

- 项目：family-storage-system（家庭收纳物品管理系统）
- 测试类型：接口自动化回归（**F2 缺陷修复验收**）+ 契约一致性抽检
- 执行者：api-tester（子 Agent，按 `/skill api-tester` 规范）
- 契约依据：`docs/api-spec.md`（§5.1 认证 / §5.2 用户 / §5.3 分类位置 / §5.4 物品 / §5.5 缺耗品 / §5.7 通知 / §5.8 统计 / §5.9 设置 / 错误码表）
- 被测构建：`backend/target/storage-0.0.1-SNAPSHOT.jar`（2026-09-29 16:23 打包，含 F2 修复；工作区未提交改动 `ItemService.java` / `docs/api-spec.md`）
- 报告时间：2026-09-29 17:01（最终轮次）

---

## 1. 结论

| 指标 | 结果 |
| --- | --- |
| **总判定** | **PASS** |
| 被测端点 | **28** 个 HTTP 端点（+1 条 SQL 前置操作） |
| 用例总数 | **89**（88 个 HTTP 请求 + 1 个 SQL 前置） |
| 通过 / 失败 | **89 / 0**，通过率 **100%** |
| 失败用例 | **0** |
| 发现缺陷 | **0**（后端实现符合契约；详见 §6 观测项，均为「契约表述/一致性」建议，非实现缺陷） |
| 性能（阈值 p95 < 500ms） | p95 = **69.5ms**，avg = 20.6ms，max = 463ms（冷启动首请求） |
| 主库隔离 | `family_storage` 前后快照**完全一致**（users 4 / items 4 / categories 1 / locations 2 / settings 1 / shopping 2，阈值仍为 7） |

**F2 核心断言 a–e 全部满足**（详见 §4）：`GET /api/items?expiring=1` 的临期窗口已改为读取 `app_settings` 的 `expiry.remind.days` 阈值，与 `GET /api/items/expiring`（恒默认 7，可被 `days` 覆盖）、`GET /api/statistics/summary.expiringSoon` 同口径。

---

## 2. 被测范围与环境

### 2.1 环境

| 项 | 值 |
| --- | --- |
| 被测地址 | `http://localhost:8080`（`java -jar target\storage-0.0.1-SNAPSHOT.jar`，`-WorkingDirectory D:\xcode\wt\backend`） |
| 运行时 | Java 17.0.12（`D:\Java\jdk-17`），Spring Boot 应用（Tomcat 8080） |
| 构建 | Maven 3.9.16（`D:\apache-maven-3.9.16`），`-DskipTests package` |
| 数据库 | MySQL 5.7.31；数据源经环境变量 `DB_URL` / `DB_PASSWORD` 注入（**报告与证据不含明文口令**） |
| 测试数据库 | **隔离库 `family_storage_apitest_f2`**（最终轮次重建，Flyway 迁移 `V1=init`、`V2=add items version`、`V3=add location description image` 全部 `success=1`；种子阈值 `expiry.remind.days=7`） |
| 主库 | `family_storage` **只读比对**，测试前后计数一致（见 §9 末尾核对表） |
| 数据构造日期 | `today = 2026-09-29`（脚本运行时取系统日期） |

### 2.2 被测端点（28）

```
POST   /api/auth/register        POST   /api/auth/login          GET  /api/auth/me
PUT    /api/auth/password        GET    /api/users               POST /api/users
GET    /api/categories           POST   /api/categories          DELETE /api/categories/{id}
GET    /api/locations            POST   /api/locations           DELETE /api/locations/{id}
GET    /api/items                POST   /api/items               GET    /api/items/{id}
PUT    /api/items/{id}           DELETE /api/items/{id}          GET    /api/items/expiring
POST   /api/items/{id}/stock     GET    /api/items/{id}/stock-changes
GET    /api/shopping             POST   /api/shopping
GET    /api/notifications        GET    /api/notifications/unread-count
GET    /api/statistics/summary   GET    /api/statistics/by-category
GET    /api/settings             PUT    /api/settings
```

SQL 前置（非 HTTP，仅用于构造归档态）：`UPDATE family_storage_apitest_f2.items SET status='ARCHIVED' WHERE id=?`。

### 2.3 测试数据（F2 固定夹具，共 10 个物品）

| 名称 | expiryDate | 相对 today | status | 阈值7 是否命中 | 阈值30 是否命中 |
| --- | --- | --- | --- | --- | --- |
| F2-D00 | 2026-09-29 | +0 | ACTIVE | ✅ | ✅ |
| F2-D03 | 2026-10-02 | +3 | ACTIVE | ✅ | ✅ |
| F2-D07 | 2026-10-06 | **+7（窗口右端点）** | ACTIVE | ✅ | ✅ |
| F2-D08 | 2026-10-07 | +8 | ACTIVE | ❌ | ✅ |
| F2-D10 | 2026-10-09 | +10 | ACTIVE | ❌ | ✅ |
| F2-D30 | 2026-10-29 | +30 | ACTIVE | ❌ | ✅ |
| F2-D45 | 2026-11-13 | +45 | ACTIVE | ❌ | ❌ |
| F2-NOEXP | null | — | ACTIVE | ❌ | ❌ |
| F2-PAST | 2026-09-26 | −3（已过期） | ACTIVE | ❌ | ❌ |
| F2-ARCH | 2026-10-02 | +3 | **ARCHIVED** | ❌（状态过滤） | ❌ |

---

## 3. 执行结果统计

### 3.1 总体

| 用例组 | 说明 | 用例数 | 通过 | 失败 |
| --- | --- | --- | --- | --- |
| S | 前置数据与会话（注册/登录、分类、位置、10 个物品、归档、成员账号） | 18 | 18 | 0 |
| **F2** | **临期阈值核心断言 a–e + 统计同口径** | **18** | **18** | **0** |
| A | 认证与账号保护 | 5 | 5 | 0 |
| I | 检索 / CRUD / 出入库 | 20 | 20 | 0 |
| E | 设置错误分支与权限（§5.9） | 10 | 10 | 0 |
| R | 只读接口抽检 + 资源权限（§5.2/5.5/5.7/5.8） | 12 | 12 | 0 |
| Z | 收尾清理与幂等校验 | 6 | 6 | 0 |
| **合计** | | **89** | **89** | **0** |

### 3.2 分组耗时（ms）

| 组 | 用例数 | p95 | max | 备注 |
| --- | --- | --- | --- | --- |
| S | 18 | 80.6 | **463.0** | S01 冷启动注册（应用首次请求 + Flyway 后首查） |
| F2 | 18 | 31.1 | 40.5 | 检索/设置写读回 |
| A | 5 | 69.5 | 72.3 | 登录/改密为 bcrypt 开销 |
| I | 20 | 27.6 | 29.5 | |
| E | 10 | 10.0 | 22.2 | |
| R | 12 | 10.5 | 15.2 | |
| Z | 6 | 11.1 | 12.0 | |
| **全部** | **89** | **69.5** | 463.0 | **avg 20.6 / min 2.6 / 阈值 p95<500ms ✅** |

---

## 4. F2 核心断言与证据

> 断言来源：F2 缺陷修复要求（`ItemService.search` 的 `expiring=1` 窗口改为读取 `app_settings.expiry.remind.days`）。
> 证据文件：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-f2\evidence\*.req.json` / `*.res.txt`（**token 已脱敏为 `***REDACTED***`**）。

| # | 断言 | 用例 | 请求 | 期望 | 实测 | 结果 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| a | 阈值=7 时窗口 `[today, today+7]` 且仅 ACTIVE | F2-01/02/03 | `GET /api/items?expiring=1&size=100` | `expiry_remind_days=7`；返回 `F2-D00,F2-D03,F2-D07`；`totalElements=3`；无 8/10/30/45 天、过期、无到期日、ARCHIVED 泄漏 | `7`；`[F2-D00,F2-D03,F2-D07]`；`totalElements=3`；`leaked=[] nonActive=0` | ✅ | `F2-01.res.txt`、`F2-02.res.txt`、`F2-03.res.txt` |
| b | PUT 阈值=30 后纳入 8~30 天物品（+10 天改前不返回） | F2-04/05/06/07 | `PUT /api/settings {"key":"expiry.remind.days","value":"30"}` → `GET /api/settings` → `GET /api/items?expiring=1&size=100` | 响应与回读均为 `30`；返回 6 项 `D00,D03,D07,D08,D10,D30`；`newlyIncluded=[F2-D08,F2-D10,F2-D30]`、`stillExcluded=[]` | `{"expiry_remind_days":30}`；`30`；`totalElements=6`；`newlyIncluded=[F2-D08,F2-D10,F2-D30] stillExcluded=[]` | ✅ | `F2-04.req.json`、`F2-04.res.txt`、`F2-06.res.txt`、`F2-07.res.txt` |
| c | 改回 7 后窗口恢复（+10 天不再返回） | F2-14/15/16/17 | `PUT {"key":"expiry.remind.days","value":"7"}` → `GET /api/settings` → `GET /api/items?expiring=1&size=100` | 回读 `7`；返回 3 项 | `7`；`[F2-D00,F2-D03,F2-D07]` `totalElements=3` | ✅ | `F2-14.res.txt`、`F2-16.res.txt` |
| d | `GET /api/items/expiring` **不随阈值变**，恒默认 7 天；`days` 可覆盖 | F2-08/09/10/17 | 阈值=30 时 `GET /api/items/expiring`；`?days=30`；`?days=45` | 默认=7 项集；days=30→6 项；days=45→7 项（含 D45） | 默认 `[F2-D00,F2-D03,F2-D07]`；30→6 项；45→7 项 | ✅ | `F2-08.res.txt`、`F2-09.res.txt`、`F2-10.res.txt` |
| e | 不带 `expiring=1` 的检索**不受阈值影响** | F2-11 | 阈值=30 时 `GET /api/items?size=100` | 全部 9 个 ACTIVE（含 NOEXP/PAST/D45） | `totalElements=9`，集合完全一致 | ✅ | `F2-11.res.txt` |
| f | 统计/调度同口径（附加） | F2-12/18 | `GET /api/statistics/summary` | 阈值30→`expiringSoon=6 totalItems=9`；阈值7→`expiringSoon=3` | `{"totalItems":9,...,"expiringSoon":6}`；恢复后 `expiringSoon=3` | ✅ | `F2-12.res.txt`、`F2-18.res.txt` |
| g | 状态过滤与组合过滤（附加） | F2-13、I20 | `GET /api/items?expiring=1&status=ARCHIVED`；`GET /api/items?keyword=D07&expiring=1` | `[F2-ARCH]`；`[F2-D07]`（+10 天的 D10 被窗口排除） | 一致 | ✅ | `F2-13.res.txt`、`I20.res.txt` |

**关键响应节选（阈值=7，`GET /api/items?expiring=1&size=100`）**

```json
{"content":[{"id":12,"name":"F2-D00",...,"expiryDate":"2026-09-29","status":"ACTIVE"},
            {"id":13,"name":"F2-D03",...,"expiryDate":"2026-10-02","status":"ACTIVE"},
            {"id":14,"name":"F2-D07",...,"expiryDate":"2026-10-06","status":"ACTIVE"}],
 "totalElements":3,"totalPages":1,"size":100,"number":0}
```

**阈值=30 后（+8/+10/+30 天被纳入，+45/过期/无到期/ARCHIVED 仍排除）**

```json
{"content":[{"name":"F2-D00"},{"name":"F2-D03"},{"name":"F2-D07"},
            {"name":"F2-D08","expiryDate":"2026-10-07"},
            {"name":"F2-D10","expiryDate":"2026-10-09"},
            {"name":"F2-D30","expiryDate":"2026-10-29"}],
 "totalElements":6,"totalPages":1,"size":100,"number":0}
```

### 4.1 其他通过面（摘要）

- **认证 A 组**：错误密码 → 401 `BAD_CREDENTIALS`；无 token → 401 `UNAUTHORIZED`；非空系统注册 → 403 `REGISTRATION_CLOSED`；旧密码错误 → 400 `WRONG_PASSWORD`；MEMBER 会话 `/api/auth/me` → `role=MEMBER`。
- **检索与 CRUD I 组**：`keyword` LIKE、`categoryId`/`locationId` 过滤、非法 `status` → 400 `INVALID_STATUS`、详情 14 字段完整、404 分支（分类/位置/物品）、负库存 → `NEGATIVE_QUANTITY`、空 name → `VALIDATION_ERROR`、PUT 回显、出入库 `IN`/`OUT`/`ADJUST` 与错误码（`INSUFFICIENT_STOCK`、`INVALID_STOCK_TYPE`、`TYPE_DELTA_MISMATCH`）、变更历史含 `INITIAL+IN`、删除 → 204 后 404。
- **设置 E 组**：401/400 `INVALID_SETTING_KEY`、`INVALID_SETTING_VALUE`（非数字 / 0 / 负数）、403（MEMBER 改设置）、`VALIDATION_ERROR`（空值，见 §6-O2）、合法值 200 回读。
- **权限 R 组**：MEMBER → `GET /api/users` 403；分页 `PageResponse` 字段齐全；购物清单 `INVALID_QUANTITY`；删除在用分类 → 400 `CATEGORY_IN_USE`；通知分页/未读数字段齐全。
- **收尾 Z 组**：10 个自建物品批量删除全 204、分类/位置删除 204、删除后列表归零（`totalElements=0`、`count=0`）。

---

## 5. 失败用例

**无（0 个）。**

> 说明（过程可追溯，非缺陷）：本轮共执行 5 次（1 次脚本层报错中止 + 4 次完整跑），最终 2 次完整跑均为 **89/89**。前 2 次完整跑的失败**全部为测试脚本自身的断言/取值缺陷**，逐一定位并修正后重跑：① 脱敏在解析前执行导致 token 失效（全量 401）；② `POST /api/categories` 返回**裸数字 id**（契约「返回新 id」），原脚本仅解析 `{id:…}` 导致下游 `id=-1` 级联；③ `param()` 不是脚本块首语句导致 R10 报错；④ `I14` 出库用正数 `deltaQty`，与契约「正入负出」冲突（正确行为为 `TYPE_DELTA_MISMATCH`）；⑤ `I01` 单元素数组被 PowerShell 管道折叠为标量，`$names[0]` 取到首字符；⑥ `I20` 期望值写错（`keyword=D10` 在阈值 7 下本就不应命中，改为窗口内的 `D07`）；⑦ `Z01` 重复删除已被 `I18` 删除的物品。后端代码**未因本次回归改动**。

---

## 6. 观测项（非阻断，建议 architect 确认 / 补注契约）

| 编号 | 现象 | 契约现状 | 建议 |
| --- | --- | --- | --- |
| O1 | `POST /api/users` 成功返回 **200**（`UserController.create` 无 `@ResponseStatus(CREATED)`） | §5.2 只写了「→ `UserResponse`」，**未定义成功状态码** | 契约补注「200」或实现改为 201，避免前后端对齐成本；本次按「200/201 均接受」断言 |
| O2 | `PUT /api/settings` 传 `value=""` → 400 **`VALIDATION_ERROR`**（Bean Validation `@NotBlank`） | §5.9 该行只列 `INVALID_SETTING_KEY`、`INVALID_SETTING_VALUE`（值「非正整数」）；但**错误码表**已含通用 `VALIDATION_ERROR` | §5.9 补一句「空值走 `VALIDATION_ERROR`」，或实现改为 `INVALID_SETTING_VALUE` 统一语义 |
| O3 | `POST /api/categories` 成功响应体为**裸数字**（如 `1`），与其它对象型响应不一致 | §5.3「新增，返回新 id」——**实现符合契约** | 属契约层面的风格问题，前端需按数字解析；建议在契约中显式标注「响应为 `application/json` 数字」 |
| O4 | `POST /api/auth/register` 成功返回 **200** | §5.1 未定义成功状态码 | 同 O1，建议契约补注（本次已按 200 断言通过） |

---

## 7. 覆盖缺口（契约中存在但本轮未测）

1. **未覆盖端点**：`GET/PUT /api/categories/{id}`、`GET/PUT /api/locations/{id}`、`PUT /api/shopping/{id}`、`PATCH /api/shopping/{id}/status`、`POST /api/files` + `DELETE /api/files/{name}`、`POST /api/notifications/{id}/read`、`POST /api/notifications/read-all`、`PUT /api/users/{id}`、`PATCH /api/users/{id}/status`、`POST /api/users/{id}/reset-password`、`GET /api/statistics/by-location`。
2. **未覆盖业务错误码**：`CATEGORY_NAME_TAKEN`、`LOCATION_CYCLE`、`LOCATION_IN_USE`、`LOCATION_HAS_CHILDREN`、`USER_NOT_FOUND`、`USERNAME_TAKEN`、`SHOPPING_ITEM_NOT_FOUND`、`NOTIFICATION_NOT_FOUND`、`EMPTY_FILE`/`INVALID_FILE_TYPE`/`FILE_TOO_LARGE`/`INVALID_FILE_PATH`、`INTERNAL_ERROR`(500)。
3. **认证深度**：JWT 过期（`expire-hours=168`）与伪造 token、连续失败限流、跨用户数据越权（他人通知 404 口径）。
4. **并发与幂等**：重复提交创建、并发 `PUT /api/settings`、同一分类并发删除（仅覆盖了顺序删除 400/204 分支）。
5. **参数边界**：分页 `page=-1` / 超大 `size`、`days=0`/负数、`keyword` 特殊字符与转义、`imageUrl` 正则（§5.3 图片口径）。
6. **阈值边界补强**：已覆盖 `today+7`（含）与 `today-1`（不含）；未覆盖阈值=0、阈值>365 等极端值的取值域校验（E05/E06 仅覆盖 0 与负数）。
7. **调度器**：`ExpiryReminderScheduler` 的定时通知生成（§5.7）属时间驱动，本轮未触发。

---

## 8. 复现方式

```powershell
# 1) 准备隔离库（绝不使用主库 family_storage）
$MYSQL = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'
& $MYSQL -uroot "-p$env:DB_PASSWORD" -e `
  "DROP DATABASE IF EXISTS family_storage_apitest_f2; CREATE DATABASE family_storage_apitest_f2 DEFAULT CHARACTER SET utf8mb4;"

# 2) 打包（F2 修复需在 jar 内）
$env:JAVA_HOME = 'D:\Java\jdk-17'
& 'D:\apache-maven-3.9.16\bin\mvn.cmd' -f 'D:\xcode\wt\backend\pom.xml' -DskipTests package

# 3) 启动被测服务（凭据全部走环境变量）
$env:DB_URL = 'jdbc:mysql://localhost:3306/family_storage_apitest_f2?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
Start-Process 'D:\Java\jdk-17\bin\java.exe' `
  -ArgumentList '-jar','target\storage-0.0.1-SNAPSHOT.jar' `
  -WorkingDirectory 'D:\xcode\wt\backend' `
  -RedirectStandardOutput "$env:TEMP\opencode\api-test-f2\server-out.log" `
  -RedirectStandardError  "$env:TEMP\opencode\api-test-f2\server-err.log"
# 等待 8080 就绪后继续

# 4) 执行回归（用例矩阵 / 断言 / 证据落盘）
powershell -NoProfile -ExecutionPolicy Bypass `
  -File 'C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-f2\run-tests-f2.ps1'
# 退出码 0 = 全部通过；summary-f2.txt / results-f2.json / evidence\ 为本次产物
```

- 凭据：优先读环境变量 `API_TEST_USER` / `API_TEST_PASS`；未设置时脚本运行时生成并落盘 `credentials.json`（**不写入报告、不入库**）。
- 单个用例的手工复现样例（需先登录取 `TOKEN`）：

```bash
# (a) 阈值=7 的临期窗口
curl -s 'http://localhost:8080/api/items?expiring=1&size=100' -H "Authorization: Bearer $TOKEN"
# (b) 修改阈值
curl -s -X PUT http://localhost:8080/api/settings -H 'Content-Type: application/json' \
     -H "Authorization: Bearer $TOKEN" -d '{"key":"expiry.remind.days","value":"30"}'
# (d) 列表端点不随阈值变（默认 7 天）
curl -s 'http://localhost:8080/api/items/expiring' -H "Authorization: Bearer $TOKEN"
curl -s 'http://localhost:8080/api/items/expiring?days=45' -H "Authorization: Bearer $TOKEN"
# (e) 不带 expiring=1 的全量检索
curl -s 'http://localhost:8080/api/items?size=100' -H "Authorization: Bearer $TOKEN"
```

---

## 9. 证据与产物索引

| 产物 | 路径 |
| --- | --- |
| 测试脚本（UTF-8 BOM，PowerShell 5.1） | `C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-f2\run-tests-f2.ps1` |
| 结构化结果（89 条，含 status/断言/latency/response） | `C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-f2\results-f2.json` |
| 汇总 | `...\api-test-f2\summary-f2.txt` |
| 请求/响应证据（**127 个文件，token 已脱敏**） | `...\api-test-f2\evidence\{ID}.req.json` / `{ID}.res.txt` |
| 主库前置快照（用于前后比对） | `...\api-test-f2\maindb-pre.txt` |
| 服务日志 | `...\api-test-f2\server-out.log`、`server-err.log`、`server.pid` |
| 关联报告 | `docs/reports/api-test-report.md`（2026-09-24 基线，79 用例 / 76 通过） |

**主库隔离核对（测试前后一致）**

| 表 | 前 | 后 |
| --- | --- | --- |
| users / items / categories / locations / settings / shopping_items | 4 / 4 / 1 / 2 / 1 / 2 | 4 / 4 / 1 / 2 / 1 / 2 |
| `app_settings.expiry.remind.days` | 7 | 7 |
