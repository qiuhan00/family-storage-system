# API 接口定向复验报告 — M1/s1 增量回归（2026-09-29）

- 项目：family-storage-system（家庭收纳物品管理系统）
- 测试类型：**定向接口回归**（F2 修复评审后追加变更 M1/s1 的验收），非全量 89 用例
- 执行者：api-tester（子 Agent，已加载 `/skill api-tester` 规范执行）
- 契约依据：`docs/api-spec.md` §5.4（`GET /api/items/expiring`：**`days` 缺省取 `expiry.remind.days` 阈值，可用 `days` 显式覆盖**，F2/M1 单一口径）、`GET /api/items?expiring=1`（F2）、§5.9 设置、§5.1 认证、§5.8 统计
- 基线报告：`docs/reports/api-test-report-2026-09-29.md`（89/89 PASS，旧契约下断言 (d)「不带 days 恒 7 天」）
- 被测构建：`backend/target/storage-0.0.1-SNAPSHOT.jar`（**2026-09-29 18:21:55 打包**，含 M1 + s1；工作区未提交改动 `ItemController.java` / `ItemService.java` / `ExpiryReminderScheduler.java` / `docs/api-spec.md`）
- 报告时间：2026-09-29 18:31

---

## 1. 结论

| 指标 | 结果 |
| --- | --- |
| **总判定** | **PASS** |
| 用例总数 | **44**（41 个 HTTP 请求 + 3 个 SQL/聚合前置与收尾） |
| 通过 / 失败 | **44 / 0**，通过率 **100%** |
| **M1 核心断言 d1–d3** | **8/8 PASS**（M1-01…M1-08）—— `days` 缺省行为已按**新契约**验证通过（缺省跟随阈值、显式覆盖生效） |
| F2 断言 a/b/c/e 复跑（d4） | **6/6 PASS**（与上轮行为一致，M1 未破坏 F2 修复） |
| 回归抽查（d5） | **4/4 PASS**（登录 401、已过期不进列表、§5.9 member 读 200 / 写 403） |
| 阈值恢复 | **已恢复 7**（API 回读 + 隔离库 SQL 双确认，见 §6） |
| 发现缺陷 | **0**（后端实现与更新后契约一致） |
| 性能（阈值 p95 < 500ms） | p95 = **68.9ms**，avg = 16.1ms，max = 73.0ms |
| 主库隔离 | `family_storage` 测试前后快照**完全一致**（4/4/1/2/1/2，阈值仍为 7，见 §9） |

**M1 增量结论**：`GET /api/items/expiring` 的 `days` 已从 `defaultValue="7"` 改为可缺省（`required=false` → `Integer days`），缺省时 `ItemService.expiringList(null)` 取 `expiry.remind.days` 阈值；显式 `days` 仍覆盖阈值。实现与 `docs/api-spec.md` §5.4 更新行一致。
**s1 结论**：`ExpiryReminderScheduler` 常量替换无行为变化——本轮实测阈值 30 档 `statistics/summary.expiringSoon=6`（与上轮 F2-12 同值）、恢复 7 档检索/列表回 3 项（与上轮 F2-16/17/18 同值），三处口径一致、未见回归；`threshold=7` 档的 `expiringSoon` 本轮未重复实测（上轮 F2-18 已覆盖），s1 的 diff 层面核对建议由 code-reviewer 补充。

---

## 2. 被测范围与环境

### 2.1 环境

| 项 | 值 |
| --- | --- |
| 被测地址 | `http://localhost:8080`（`java -jar target\storage-0.0.1-SNAPSHOT.jar`，WorkingDirectory `D:\xcode\wt\backend`，PID 27328，18:23:08 启动 / 18:32 停止） |
| 构建 | Maven 3.9.16 + JDK 17，`-DskipTests package`，成功（18:21:55，61,027,292 字节） |
| 数据库 | MySQL 5.7.31；数据源经环境变量 `DB_URL` / `DB_PASSWORD` 注入（**报告与证据不含明文口令**） |
| 测试数据库 | **隔离库 `family_storage_apitest_f2`**（复用上轮；Flyway `Current version: 3`，`Schema is up to date`，种子阈值 `expiry.remind.days=7`） |
| 主库 | `family_storage` **只读比对**，测试前后计数与阈值完全一致 |
| 数据构造日期 | `today = 2026-09-29`（脚本运行时取系统日期） |
| 服务收尾 | 已 `Stop-Process`，**8080 已释放**，无残留 java 进程；`server-err.log` 0 字节，`server-out.log` 无 ERROR/Exception |

### 2.2 测试夹具（10 个物品，同上轮 F2 固定矩阵）

| 名称 | expiryDate | 相对 today | status | 阈值7 | 阈值30 | days=45 |
| --- | --- | --- | --- | --- | --- | --- |
| F2-D00 / D03 / D07 | +0 / +3 / +7 | 窗口内 | ACTIVE | ✅ | ✅ | ✅ |
| F2-D08 / D10 / D30 | +8 / +10 / +30 | 7 外 30 内 | ACTIVE | ❌ | ✅ | ✅ |
| F2-D45 | +45 | 30 外 45 内 | ACTIVE | ❌ | ❌ | ✅ |
| F2-NOEXP | null | — | ACTIVE | ❌ | ❌ | ❌ |
| F2-PAST | −3（已过期） | 今天之前 | ACTIVE | ❌ | ❌ | ❌ |
| F2-ARCH | +3 | 窗口内 | **ARCHIVED** | ❌ | ❌ | ❌ |

### 2.3 用例组

```
S   前置：登录 ADMIN / me / 分类 / 位置 / 10 物品 / SQL 归档 / MEMBER 建号登录 / 阈值归位 7   20 例
M1  定向：d1(d) + d4(F2-a) + d2(M1 核心) + d3(显式覆盖) + d4(F2-b/e/统计) + d5 回归抽查      15 例
Z   收尾：阈值恢复 7（API×2 + 检索×2 + SQL×1）+ 清理 10 物品/分类/位置 + 归零回读             9 例
```

---

## 3. 执行结果统计

### 3.1 总体

| 用例组 | 说明 | 用例数 | 通过 | 失败 |
| --- | --- | --- | --- | --- |
| S | 前置数据与会话 | 20 | 20 | 0 |
| **M1** | **M1/s1 定向断言（d1–d5）** | **15** | **15** | **0** |
| Z | 阈值恢复 + 清理 | 9 | 9 | 0 |
| **合计** | | **44** | **44** | **0** |

### 3.2 分组耗时（ms，SQL 前置不计）

| 组 | 计时用例 | p95 | avg | max |
| --- | --- | --- | --- | --- |
| S | 19 | 73.0 | 20.1 | 73.0 |
| M1 | 15 | 68.9 | 13.9 | 68.9 |
| Z | 7 | 12.8 | 9.9 | 12.8 |
| **全部** | **41** | **68.9** | **16.1** | **73.0**（阈值 p95<500ms ✅） |

---

## 4. M1 断言 d1–d3 证据（按更新后 spec 断言）

> 证据文件：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-m1\evidence\{ID}.req.json` / `{ID}.res.txt`（**token 与口令均已脱敏为 `***REDACTED***`**，含请求体）。
> 夹具 id：items 22–31（F2-D00…F2-ARCH），category/location = 3。

### d1) 阈值=7（初始）：不带 `days` → `[today, today+7]`

| 用例 | 请求 | 期望 | 实测 | 结果 |
| --- | --- | --- | --- | --- |
| M1-01 | `GET /api/items/expiring`（**无 days**） | 200；集合 `[F2-D00, F2-D03, F2-D07]` | 200；3 项，expiryDate `2026-09-29 / 2026-10-02 / 2026-10-06`，全部 ACTIVE | ✅ |
| M1-02 | 同上（泄漏面检查） | 无 D08/D10/D30/D45/PAST/NOEXP/ARCH；无非 ACTIVE | `leaked=[] nonActive=0` | ✅ |
| M1-03 | `GET /api/items?expiring=1&size=100`（d4/F2-a） | 3 项同上 | `totalElements=3` | ✅ |

响应节选（M1-01，与旧默认 7 天窗口一致）：

```json
[{"id":22,"name":"F2-D00","expiryDate":"2026-09-29","status":"ACTIVE"},
 {"id":23,"name":"F2-D03","expiryDate":"2026-10-02","status":"ACTIVE"},
 {"id":24,"name":"F2-D07","expiryDate":"2026-10-06","status":"ACTIVE"}]
```

### d2) 阈值改 30：不带 `days` → 窗口扩为 `[today, today+30]`（M1 核心）

| 用例 | 请求 | 期望 | 实测 | 结果 |
| --- | --- | --- | --- | --- |
| M1-04 | `PUT /api/settings {"key":"expiry.remind.days","value":"30"}` | 200，`expiry_remind_days=30` | 200 `{"expiry_remind_days":30}` | ✅ |
| M1-05 | `GET /api/settings` 回读 | 30 | 30 | ✅ |
| **M1-06** | **`GET /api/items/expiring`（无 days）** | **6 项 `[D00,D03,D07,D08,D10,D30]`（缺省跟随阈值）** | **6 项，新增 `F2-D08(10-07) F2-D10(10-09) F2-D30(10-29)`，无 D45/PAST/NOEXP/ARCH** | ✅ |

响应节选（M1-06）：

```json
[{"name":"F2-D00","expiryDate":"2026-09-29"}, {"name":"F2-D03","expiryDate":"2026-10-02"},
 {"name":"F2-D07","expiryDate":"2026-10-06"}, {"name":"F2-D08","expiryDate":"2026-10-07"},
 {"name":"F2-D10","expiryDate":"2026-10-09"}, {"name":"F2-D30","expiryDate":"2026-10-29"}]
```

> 对照：上轮（旧契约）同请求恒返回 3 项；本轮返回 6 项 —— 即 **M1 变更的可观测差异已按新 spec 验证生效**。

### d3) 阈值=30 下显式 `days` 覆盖

| 用例 | 请求 | 期望 | 实测 | 结果 |
| --- | --- | --- | --- | --- |
| M1-07 | `GET /api/items/expiring?days=7` | 3 项（7 天窗口，不受阈值 30 影响） | `[F2-D00, F2-D03, F2-D07]` | ✅ |
| M1-08 | `GET /api/items/expiring?days=45` | 7 项（含 `F2-D45`） | `[F2-D00, F2-D03, F2-D07, F2-D08, F2-D10, F2-D30, F2-D45]` | ✅ |
| M1-13 | `GET /api/items/expiring?days=100`（d5 边界附带） | 7 项且**已过期不进** | 7 项；`PAST=False ARCH=False NOEXP=False` | ✅ |

---

## 5. d4（F2 断言复跑）与 d5（回归抽查）

### 5.1 d4 — F2 a/b/c/e 在阈值 7/30 切换下的窗口变化

| 断言 | 用例 | 请求（阈值态） | 期望 | 实测 | 结果 |
| --- | --- | --- | --- | --- | --- |
| a | M1-03 | `GET /api/items?expiring=1&size=100` @7 | 3 项 | `totalElements=3` `[D00,D03,D07]` | ✅ |
| b | M1-09 | 同上 @30 | 6 项（纳入 8/10/30 天） | `totalElements=6` `[D00,D03,D07,D08,D10,D30]` | ✅ |
| c | Z03 | 同上 @恢复 7 | 回到 3 项 | `totalElements=3` | ✅ |
| e | M1-10 | `GET /api/items?size=100`（**不带 expiring**）@30 | 9 个 ACTIVE 全量，不受阈值影响 | `totalElements=9`，含 NOEXP/PAST/D45，集合完全一致 | ✅ |
| 同口径 | M1-11 | `GET /api/statistics/summary` @30 | `expiringSoon=6 totalItems=9` | `{"totalItems":9,...,"expiringSoon":6}` | ✅ |
| PUT 回读 | M1-04/05、Z01/02 | `PUT`+`GET /api/settings` | 写读一致 | 30→30、7→7 | ✅ |

### 5.2 d5 — 回归抽查

| 用例 | 请求 | 期望 | 实测 | 结果 |
| --- | --- | --- | --- | --- |
| M1-12 | `POST /api/auth/login` 错误口令 | 401 `BAD_CREDENTIALS` | 401 `{"code":"BAD_CREDENTIALS","message":"用户名或密码错误"}` | ✅ |
| M1-13 | `GET /api/items/expiring?days=100` | 已过期 `F2-PAST` 不进列表 | `PAST=False`（窗口左端点为 today） | ✅ |
| M1-14 | §5.9 `GET /api/settings`（**MEMBER**） | 200 可读 | 200 `{"expiry_remind_days":30}` | ✅ |
| M1-15 | §5.9 `PUT /api/settings`（**MEMBER**） | 403 `FORBIDDEN` | 403 `{"code":"FORBIDDEN","message":"无权限"}` | ✅ |

---

## 6. 阈值恢复确认（收尾，三重证据）

| 用例 | 校验 | 结果 |
| --- | --- | --- |
| Z01 | `PUT /api/settings {"value":"7"}` → 响应 `{"expiry_remind_days":7}` | ✅ |
| Z02 | `GET /api/settings` 回读 → `7` | ✅ |
| Z03 | `GET /api/items?expiring=1&size=100` → 3 项（阈值生效） | ✅ |
| Z04 | `GET /api/items/expiring`（**不带 days**）→ 3 项（M1 缺省行为随阈值回到 7） | ✅ |
| Z05 | 隔离库 SQL `SELECT value FROM family_storage_apitest_f2.app_settings WHERE \`key\`='expiry.remind.days'` | `db value=7` ✅ |

**结论：测试结束时 `expiry.remind.days = 7`（API 与 DB 一致），主库阈值全程未动（仍为 7）。**

---

## 7. 失败用例

**最终轮：0 个。**

> 过程可追溯（非后端缺陷）：本轮执行 2 次。第 1 次 43/44，唯一失败 **Z05 为测试脚本自身缺陷**——PowerShell 双引号字符串中的反引号 `` ` `` 被当作转义符吞掉，导致 SQL 里 `` `key` `` 变成裸 `key`（保留字）而报 `ERROR 1064`；改用单引号字符串（`''` 转义）后重跑 44/44。**后端代码未因该失败改动**；同批次 M1 断言在第 1 次已全部通过。

---

## 8. 覆盖缺口（本轮定向范围之外，契约存在但未测）

1. **`days` 参数取值域**（契约未定义，需 architect 确认口径）：`days=0`、负数、非数字（Spring 类型转换 400 还是空结果？）、超大值（>365）。本轮仅覆盖 7 / 45 / 100。
2. **缺省语义的其它入口**：`POST /api/notifications` 手动触发、`ExpiryReminderScheduler` 定时任务（09:00 cron）本身未被本轮触发——s1 常量替换仅由「统计/检索/列表口径与上轮一致」间接佐证；建议后端补一条调度器单测钩子或由 code-reviewer 核对 diff。
3. **并发/幂等**：并发 `PUT /api/settings` 与同时 `GET /api/items/expiring` 的读写一致性；重复 `days` 查询缓存行为。
4. **全量 89 用例**：本轮按要求仅做定向回归，上轮报告的 I/A/E/R 组未复跑（未受本次变更影响的端点）。
5. 上轮报告 §7 的历史缺口（文件上传错误码、分页边界、JWT 过期等）仍待后续轮次补齐。

---

## 9. 主库隔离核对（前后一致）

| 表 / 项 | 测试前 | 测试后 |
| --- | --- | --- |
| users / items / categories / locations / app_settings / shopping_items | 4 / 4 / 1 / 2 / 1 / 2 | 4 / 4 / 1 / 2 / 1 / 2 |
| `family_storage.app_settings.expiry.remind.days` | 7 | 7 |

快照文件：`...\api-test-f2\m1-maindb-pre.txt` / `m1-maindb-post.txt`。
隔离库收尾态：`items=0 categories=0 locations=0`，阈值 `7`（夹具已清理，可供下轮复用）。

---

## 10. 复现方式

```powershell
# 1) 打包（M1/s1 已在工作区）
$env:JAVA_HOME = 'D:\Java\jdk-17'
& 'D:\apache-maven-3.9.16\bin\mvn.cmd' -f 'D:\xcode\wt\backend\pom.xml' -DskipTests package

# 2) 启动被测服务（指向隔离库，凭据走环境变量）
$env:DB_URL = 'jdbc:mysql://localhost:3306/family_storage_apitest_f2?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
Start-Process 'D:\Java\jdk-17\bin\java.exe' `
  -ArgumentList '-jar','target\storage-0.0.1-SNAPSHOT.jar' -WorkingDirectory 'D:\xcode\wt\backend' `
  -RedirectStandardOutput "$env:TEMP\opencode\api-test-m1\server-out.log" `
  -RedirectStandardError  "$env:TEMP\opencode\api-test-m1\server-err.log"

# 3) 定向回归（44 用例；退出码 0 = 全部通过）
powershell -NoProfile -ExecutionPolicy Bypass `
  -File 'C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-m1\run-tests-m1.ps1'

# 4) 收尾：停进程、释放 8080
Get-Process java | Stop-Process -Force
```

单条手工复现（需先 `POST /api/auth/login` 取 `TOKEN`；阈值 7/30 用 `PUT /api/settings` 切换）：

```bash
# d1：阈值 7 下缺省 days
curl -s 'http://localhost:8080/api/items/expiring' -H "Authorization: Bearer $TOKEN"
# d2：阈值 30 后缺省 days（M1 核心）
curl -s -X PUT http://localhost:8080/api/settings -H 'Content-Type: application/json' \
     -H "Authorization: Bearer $TOKEN" -d '{"key":"expiry.remind.days","value":"30"}'
curl -s 'http://localhost:8080/api/items/expiring' -H "Authorization: Bearer $TOKEN"   # 期望 6 项
# d3：显式覆盖
curl -s 'http://localhost:8080/api/items/expiring?days=7'  -H "Authorization: Bearer $TOKEN"   # 期望 3 项
curl -s 'http://localhost:8080/api/items/expiring?days=45' -H "Authorization: Bearer $TOKEN"   # 期望 7 项
# 收尾：恢复 7
curl -s -X PUT http://localhost:8080/api/settings -H 'Content-Type: application/json' \
     -H "Authorization: Bearer $TOKEN" -d '{"key":"expiry.remind.days","value":"7"}'
```

---

## 11. 证据与产物索引

| 产物 | 路径 |
| --- | --- |
| 测试脚本（UTF-8 BOM，PowerShell 5.1） | `C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-m1\run-tests-m1.ps1` |
| 结构化结果（44 条，含 status/断言/latency/response，**口令与 token 已脱敏**） | `...\api-test-m1\results-m1.json` |
| 汇总 | `...\api-test-m1\summary-m1.txt` |
| 请求/响应证据（**71 个文件，token 与 password 均已脱敏**，泄漏扫描 0 命中） | `...\api-test-m1\evidence\{ID}.req.json` / `{ID}.res.txt` |
| 主库前置/后置快照 | `...\api-test-f2\m1-maindb-pre.txt`、`m1-maindb-post.txt` |
| 服务日志 | `...\api-test-m1\server-out.log`（无 ERROR）、`server-err.log`（0 字节）、`server.pid` |
| 关联报告 | `docs/reports/api-test-report-2026-09-29.md`（上轮 F2 全量 89/89）；`docs/reports/api-test-report.md`（基线 79 用例） |
