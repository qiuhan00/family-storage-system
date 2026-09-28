# API 接口回归测试报告

- 项目：family-storage-system（后端）
- 测试日期：2026-09-24
- 测试人员：api-tester（子 Agent）
- 基线：worktree `family-storage-system`，HEAD `c3aa12b`，分支 `feat/family-storage-system`
- 契约来源：`docs/superpowers/specs/2026-09-24-家庭收纳管理系统-design.md`（§4 / §5.1~§5.9）
- 后端运行方式：`java -jar`（Maven spring-boot:run），Spring Boot 3.3.5 / Spring Web 6.1.14 / Tomcat 10.1.31 / MySQL 5.7
- 测试脚本与证据：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\`（run-tests.ps1 / results.json / evidence\*.res.txt）

## 一、总体结论

| 指标 | 数值 |
| --- | --- |
| 被测端点 | 43 个（含 10 个子模块端点） |
| 用例总数 | 79 |
| 通过 | 76 |
| 失败 | 3 |
| 通过率 | 96.2% |
| 失败用例 | F04、F05、E02 |

结论：**接口核心链路基本可用，但存在 1 个阻断级缺陷（F05 文件删除不可用）与 2 个契约违约缺陷（F04 超限返回码错误、E02 设置写入 500）**，验收不通过，需后端修复后复测。

## 二、覆盖矩阵（契约 §5）

| 契约章节 | 端点 | 用例 | 结果 |
| --- | --- | --- | --- |
| §5.1 认证 | /api/auth/register,login,/me,password | A00-A08（9） | 全部通过 |
| §5.2 用户 | /api/users CRUD + 上下启用/重置密码 | U01-U09（9） | 全部通过 |
| §5.3 目录/位置 | /api/categories /api/locations | C01-C04、L01-L08（12） | 全部通过 |
| §5.4 物品 | /api/items 检索/详情/库存/临期/删除 | I01-I21（21） | 全部通过 |
| §5.5 购物 | /api/shopping CRUD + BOUGHT 状态机 | S01-S09（9） | 全部通过 |
| §5.6 文件 | /api/files 上传/删除 | F01-F06（6） | **F04、F05 失败** |
| §5.7 通知 | /api/notifications 未读/列表/已读 | N01-N04（4） | 全部通过 |
| §5.8 设置 | /api/settings 读写/权限/校验 | E01-E06（6） | **E02 失败** |
| §5.9 统计 | /api/stats summary/by-category/by-location | P01-P03（3） | 全部通过 |

- 通知模块说明：写通知由定时任务（cron 每日 09:00）触发，无法手动触发，按约定**降级为「端点可用性 + 返回结构合法性」验证**（N01-N04 均通过）。
- 权限边界验证：无 token 401、普通成员越权 403、禁用用户登录 401 均覆盖通过。

## 三、失败用例明细（缺陷清单）

### D1（HIGH，阻断）F05 —— DELETE /api/files/{name} 永远返回 400

- 用例：上传成功（磁盘上文件真实存在）后按返回 URL 删除 → 期望 204，实际 400
- 复现请求（curl，token 为登录返回）：
  ```
  DELETE /api/files/202609/1ed37046-3418-4506-a264-6358bc2a7e57.png
  Authorization: Bearer <token>
  ```
  响应：`HTTP 400 {"message":"非法文件路径","code":"INVALID_FILE_PATH"}`
- 附加实验：对 `flat_probe.png`（上传根目录真实文件）、`plainfile`（无扩展名真实文件）删除均返回同样的 400。**任何合法文件名都会被拒绝**。
- 根因（已用源码 + Spring 库实测确认）：
  1. `FileController.delete` 使用 `@DeleteMapping("/{*name}")`。用 spring-web 6.1.14 的 `PathPatternParser` 实测：`/api/files/{*name}` 对 `flat_probe.png` 捕获的变量是 `/flat_probe.png`（**含前导斜杠**）。
  2. `FileStorageService.delete` 执行 `uploadDir.resolve(fileName).normalize()`；传入文件名以 `/` 开头时，在路径语义上被视为绝对路径：Windows 下 `resolve("/flat_probe.png")` 会落到驱动器根（`D:\flat_probe.png`），`target.startsWith(uploadDir)` 恒 false → 抛 `INVALID_FILE_PATH`。
  3. 该缺陷为**全平台缺陷**（非 Windows 环境 `/xxx` 同样解析到文件系统根目录），文件删除功能完全不可用。
- 修复建议：在 `delete` 入口对文件名做剥除前导 `/`（如 `fileName.replaceFirst("^/+", "")`），再与 `uploadDir` 拼接并保持 `startsWith` 防穿越校验（F06 依赖此校验）。

### D2（HIGH）E02 —— PUT /api/settings 白名单键写入返回 500

- 用例：ADMIN 设置 `expiry.remind.days=10` → 期望 200，实际 500
- 复现请求：
  ```
  PUT /api/settings
  Authorization: Bearer <token>
  {"expiry.remind.days": 10}
  ```
  响应：`HTTP 500 {"message":"服务器内部错误","code":"INTERNAL_ERROR"}`
- 服务端日志：`SQL Error: 1064 ... near 'key='expiry.remind.days'' at line 1`
- 根因：`AppSetting` 实体将字段映射为 `@Column(name="key")`，而 `key` 是 MySQL 保留字；Hibernate 生成 UPDATE 语句时未加反引号，导致 SQL 语法错误 → `GlobalExceptionHandler` 兜底成 500。GET 正常是因为 SELECT 语法可用。
- 修复建议：将列名重命名（如 `setting_key`）或在 DDL/Hibernate 中统一使用反引号转义，并同步 Flyway 迁移脚本与实体注解。

### D3（MEDIUM）F04 —— 上传超过 5MB 返回 500 而非契约的 400 FILE_TOO_LARGE

- 用例：上传 6MB 文件 → 期望 400 `FILE_TOO_LARGE`，实际 500 `INTERNAL_ERROR`
- 复现请求：
  ```
  POST /api/files  (multipart, field=file)
  Authorization: Bearer <token>
  ```
  响应：`HTTP 500 {"message":"服务器内部错误","code":"INTERNAL_ERROR"}`
- 附加实验：5243080 字节（约 5MB+200B）与 5485760 字节上传均返回 500；`spring.servlet.multipart.max-file-size: 5MB` 配置存在。
- 根因（推断）：multipart 大小超限时抛出的实际异常类型不是 `MaxUploadSizeExceededException`（`FileController` 只对该类型声明了 `@ExceptionHandler` → 400 FILE_TOO_LARGE），而是被包装的 `MultipartException` 或其他类型，落入 `GlobalExceptionHandler.handleOther` → 500。服务端日志中未出现 MaxUploadSizeExceeded 相关堆栈，佐证异常类型不匹配。
- 修复建议：在 `GlobalExceptionHandler` 增加对 `MultipartException`（含 `MaxUploadSizeExceededException`）的显式处理返回 400 `FILE_TOO_LARGE`；或验证实际异常类型后补充对应 `@ExceptionHandler`。

## 四、覆盖缺口

- 通知的「业务写通知」路径：因调度器 cron（每日 09:00）不可手动触发，未验证真实通知生成内容，仅验证端点可用与结构合法（契约可接受降级）。
- `PATCH` / `PUT` 幂等与并发（如重复提交购物 BOUGHT、同物品并发库存变更）：未做真实并发压力，仅做顺序幂等验证。
- 文件「浏览/下载」端点：契约未定义独立下载端点（仅静态 `/uploads/**`），未测。
- 5xx 兜底响应：`INTERNAL_ERROR` 引发后响应体结构校验通过，但契约未明确要求 500 固定结构。

## 五、可复现性

- 全量用例脚本：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\run-tests.ps1`
- 结果 JSON：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\results.json`
- 单用例响应证据：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\evidence\{用例id}.res.txt`
- 服务端日志：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\server-out.log`（E02 SQL 1064 堆栈、启动信息）

## 六、回归建议

1. 先修 D1（阻断）与 D2，再修 D3；修复后重跑本报告附带的 79 用例（尤其 F04/F05/E02）。
2. 建议补充：删除端点对「前导斜杠、双斜杠、反斜杠」的用例，防止路径规范化回归。

---

## 七、复测（D1-D3 修复后，2026-09-24）

- 修复基线：`bc52e1c`（fix: leading-slash file delete, mysql reserved key quoting, multipart size 400）
- 环境与首测一致（空库重建 → Flyway 重建 schema → 后端 PID 9104 / Tomcat 8080）

### 7.1 全量回归

| 指标 | 数值 |
| --- | --- |
| 重跑用例 | 79 |
| 通过 | **79（100%）** |
| 失败 | 0 |

脚本与证据：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\run-tests.ps1`（复用）、`results.json`（覆盖为复测结果）、`evidence\*.res.txt`。

### 7.2 原缺陷复核（专项证据）

| 用例 | 首测 | 复测 | 证据（evidence\*.res.txt） |
| --- | --- | --- | --- |
| F04 上传 >5MB | 500 INTERNAL_ERROR | **400 FILE_TOO_LARGE** | F04: `HTTP 400 {"code":"FILE_TOO_LARGE","message":"文件大小不能超过 5MB"}` |
| F05 删除上传文件 | 400 INVALID_FILE_PATH | **204** | F05: `HTTP 204` |
| E02 PUT 白名单设置 | 500 INTERNAL_ERROR | **200** | E02: `HTTP 200 {"expiry_remind_days":10}` |

### 7.3 删除端点斜杠回归（回归建议 #2 专项）

新增 5 个形态用真实上传文件逐一验证（新 token，ADMIN）：

| 编号 | 形态 | 结果 | 判定 |
| --- | --- | --- | --- |
| R01 | 正常相对路径 `202609/uuid.png` | 204 删除成功 | 通过 |
| R02 | 前导双斜杠 `/api/files//uuid.png` | 401（未认证层拒绝） | 通过（不误删） |
| R03 | 内部双斜杠 `202609//uuid.png` | 401（未认证层拒绝） | 通过（不误删） |
| R04 | 反斜杠 `202609%5Cuuid.png` | 400 Bad Request（Tomcat 层拒绝） | 通过（不误删） |
| R05 | 穿越 `..%2F…` | 400 Bad Request（Tomcat 层拒绝） | 通过（防穿越） |

说明：R01 为 D1 修复核心路径（`FileStorageService.delete` 剥除前导 `/`、`\` 后规范化），已确认修复。R02-R05 为畸形/恶意形态，均被安全层拒绝且不会删除文件；其中 R02/R03 落在认证层（Tomcat/Spring 对双斜杠路径的匹配结果而非服务层），R04/R05 被 Tomcat 400 拦截，符合防御预期，不作为新缺陷。

### 7.4 复测结论

- **79/79 全部通过，3 个原缺陷（D1/D2/D3）全部修复且未引入回归。**
- 删除端点斜杠回归 5/5 通过（合法路径可删，畸形/穿越被拒绝）。
- **Status=API_PASS**（本次复测验收通过）。

---

## 八、复测二（code-review 修复后，2026-09-25）

- 修复基线：`453fad7`（修复提交 `13bb126`：M1-M7 + 生产 JWT 密钥兜底 + 价格边界）
- 环境与首测一致：空库重建（DROP/CREATE `family_storage`，utf8mb4）→ Flyway V1/V2 重建 schema → 后端 spring-boot:run（Tomcat 8080）
- 脚本与证据：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\run-tests-v2.ps1`、`results-v2.json`、`evidence\*.res.txt`

### 8.1 全量回归（原 79 用例 + 新增 29 个修复回归用例）

| 指标 | 数值 |
| --- | --- |
| 用例总数 | 108 |
| 通过 | **108（100%）** |
| 失败 | 0 |
| 平均耗时 | 约 18ms/用例（含并发 burst 为 0ms 汇总） |

- 原 79 用例（A/U/C/L/I/S/F/N/E/P 各模块）全部继续通过，无回归。
- 新增修复回归（X 系列，29 例）全部通过：

| 修复点 | 新增用例 | 结果 |
| --- | --- | --- |
| 生产密钥无兜底（H1，非接口项） | —（人工检查 `deploy/start.sh` 存在 `JWT_SECRET` 非空 + `>=32` 字节校验；`application-prod.yml` `secret: ${JWT_SECRET}` 无默认值） | ✅ 已修 |
| M6 最后启用 ADMIN 保护 | X01 自停用→400 `LAST_ADMIN`；X02 降级为 MEMBER→400 `LAST_ADMIN`；X70-X75 存在第二个 ADMIN 时停用/恢复/旧 token 全链路 200/401 正控 | ✅ 已修 |
| M1 禁用用户旧 token 立即失效 | X10-X14：m1probe 登录→停用→旧 token 访问 `/me`→401 `USER_DISABLED` | ✅ 已修 |
| M3 登录限流 | X20-X22：5 次失败全 401→第 6 次 429 `TOO_MANY_ATTEMPTS`；成功登录清零后 3f+ok+3f 序列全部 401 | ✅ 已修 |
| M4 畸形输入统一 400 | X30 畸形 JSON→400 `BAD_REQUEST`；X31 `categoryId=abc` 类型错→400 `BAD_REQUEST` | ✅ 已修 |
| M5 上传魔数校验 | X40 文本伪装 .jpg→400 `INVALID_FILE_TYPE`；X41 PNG 扩展名+JPEG 魔数→400；X42 真实 WebP（RIFF....WEBP）→200；X43 删除 webp→204；F04（>5MB 在魔数校验前）仍为 400 `FILE_TOO_LARGE` | ✅ 已修 |
| 价格边界 | X50 负数→400 `INVALID_PRICE`；X51 超上限 99999999.99→400；X52 3 位小数→400；X53 0.00 边界→201 | ✅ 已修 |
| M2 出入库并发（乐观锁） | X60-X62：正序 IN+2→200；100 个真实异步并发 IN+1 → 200/409 混合、无丢失更新（finalQty==expected），**但见 8.2 新缺陷** | ✅ 核心已修 + ⚠️ 见 8.2 |
| M7 注册关闭 | A01（系统非空时注册→403 `REGISTRATION_CLOSED`）沿用 | ✅ 已修 |

### 8.2 M2 并发专项与**新发现缺陷**

- 100 个并发 IN+1（.NET HttpClient 异步同时到达）实测分布：**200=15、409=15、500=70、其他=0**；finalQty=117=100+2+15（**无丢失更新**，200/409 语义正确）。
- 验证结论（M2 核心）：乐观锁冲突 → 409 `STOCK_CONFLICT` ✅；正常路径 → 200 ✅；版本并发不丢数据 ✅。
- **新缺陷（NEW-D1，MEDIUM）**：高并发下 MySQL 死锁 → Hibernate `LockAcquisitionException`（服务端日志 `Deadlock found when trying to get lock; try restarting transaction`）未被 `GlobalExceptionHandler` 映射，落入兜底 → **HTTP 500 `INTERNAL_ERROR`**，而非契约意图的 409 `STOCK_CONFLICT`（重试提示）。数据无损坏（死锁事务整体回滚），但对客户端表现为 5xx。
- 复现请求（token 为登录返回；对同一物品 50+ 并发同刻 POST 即可稳定触发）：
  ```
  POST /api/items/{id}/stock
  Authorization: Bearer <token>
  {"deltaQty":1,"type":"IN"}
  ```
- 修复建议：`GlobalExceptionHandler` 增补 `LockAcquisitionException`/`CannotAcquireLockException`/`PessimisticLockingFailureException` 映射 409 `STOCK_CONFLICT`（与乐观锁同一语义），或对死锁加 Spring Retry（MySQL 官方建议「try restarting transaction」）；不影响本次验收主结论。

### 8.3 复测二结论

- **108/108 全部通过；8 项 code-review 修复（H1 + M1-M7 + 价格边界）全部在接口层验证有效，未引入旧功能回归。**
- 并发专项暴露 1 个新缺陷（死锁→500），建议后端按 8.2 修复后补一条死锁映射单测。
- **Status=API_PASS**（复测二验收通过，附带新缺陷 NEW-D1 待后端处理）

---

## 九、locations 说明/图片字段回归（2026-09-28）

### 9.1 背景与基线

- 变更点：locations 域新增 `description`（位置具体说明 ≤500 字）与 `imageUrl`（位置照片，由 `POST /api/files` 上传回填），Flyway `V3__add_location_description_image.sql`；契约见 `docs/api-spec.md` §5.3。
- 测试目标：① 新字段全链路（写入 → 树形/详情回读 → 更新 → 置 null 清空 → 边界校验）；② 图片上传回填链路；③ locations 域既有行为回归（树形嵌套、详情、404/400 业务码、删除约束）。
- 环境：后端 `http://localhost:8080`（`java -jar storage-0.0.1-SNAPSHOT.jar`，进程 2026-09-28 14:52:25 启动，未重启），隔离库 `family_storage_e2e`，Flyway V1–V3 已应用。
- 执行时间：2026-09-28 15:08:25 – 15:08:28。
- 脚本 / 结果 / 证据：
  - 脚本：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\run-tests-loc.ps1`（含中文串 `strings-loc.json`，UTF-8 BOM）
  - 结果：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\results-loc.json`（摘要 `results-loc.txt`）
  - 逐用例响应证据：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\evidence-loc\{用例编号}.res.txt`

### 9.2 环境偏差说明（不计入产品缺陷）

1. 任务给定「库当前为空、注册开放」。本测试**首次尝试**（脚本未带 UTF-8 BOM，PowerShell 5.1 按 ANSI 解析含中文脚本导致参数绑定失败）中止前已成功执行 `POST /api/auth/register` 创建临时 ADMIN 账号；**该中止属测试工具问题，非产品缺陷**，修正编码后重跑。
2. 重跑时注册返回 **403 `REGISTRATION_CLOSED`**（系统非空 → 关闭注册，符合契约 §5.1，非缺陷），故 A00 用例改为「注册失败则用同一账号登录」，登录 200 且 `user.role=ADMIN`，token 用于全部后续用例。
3. 测试前置断言 N01：`GET /api/locations` = `[]`（空树）通过；测试**结束后已清理**本次创建的 5 个位置、1 个分类与 1 张上传图片，树/分类回到空态（仅保留上述临时 ADMIN 账号，注册后不可删）。

### 9.3 总体统计

| 指标 | 数值 |
| --- | --- |
| 用例总数 | **37** |
| 通过 | **37（100%）** |
| 失败 | **0** |
| 新发现缺陷 | **0** |
| 总耗时 | 2858 ms（含脚本开销） |
| 单请求耗时 | 最小 7.0 ms / 最大 69.4 ms（登录）；业务请求 p95 ≈ 22 ms，均 < 500 ms 阈值 |

| 分组 | 编号范围 | 用例数 | 通过 | 失败 |
| --- | --- | --- | --- | --- |
| A 环境/认证 | A00 | 1 | 1 | 0 |
| N 新字段 description / imageUrl | N01–N15 | 15 | 15 | 0 |
| M 图片链路（files → imageUrl → 回读） | M01–M06 | 6 | 6 | 0 |
| R 既有行为回归 | R01–R15 | 15 | 15 | 0 |

### 9.4 新字段用例明细（N）

| 编号 | 用例 | 请求 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- | --- | --- |
| N01 | 空库树形列表 | `GET /api/locations` | 200 `[]` | 200，count=0，`isArray=True` | 通过 |
| N02 | 创建带 `description`+`imageUrl` | `POST /api/locations` `{"name":"校验位置","parentId":null,"sortOrder":1,"description":"吊柜顶层放干货","imageUrl":"/uploads/202609/test.png"}` | 201 回显两字段 | 201，`{"id":1,...,"description":"吊柜顶层放干货","imageUrl":"/uploads/202609/test.png"}` | 通过 |
| N03 | 树形响应含新字段 | `GET /api/locations` | 200 且节点字段与写入一致 | 200，found/descEq/imgEq=True | 通过 |
| N04 | 详情含新字段 | `GET /api/locations/1` | 200 回读一致 | 200，descEq/imgEq=True | 通过 |
| N05 | 创建不带新字段 | `POST /api/locations` `{"name":"无值位置","parentId":null,"sortOrder":2}` | 201 且两字段为 null | 201，`"description":null,"imageUrl":null`（键存在、值为 null） | 通过 |
| N06 | 树形无值节点为 null | `GET /api/locations` | 200，该节点两字段 null | 200，descNull/imgNull=True，响应含 `"description":null` | 通过 |
| N07 | 更新 description | `PUT /api/locations/2` `{"description":"更新后的位置说明","imageUrl":null,...}` | 200 回显新值 | 200，descEq=True，imgNull=True | 通过 |
| N08 | 传 null 清空两字段 | `PUT /api/locations/1` `{"description":null,"imageUrl":null,...}` | 200 回显 null | 200，descNull/imgNull=True | 通过 |
| N09 | 清空后回读 | `GET /api/locations/1` | 200 两字段 null | 200，null 键值均存在 | 通过 |
| N10 | description 501 字 | `POST /api/locations`（`'a'×501`） | 400 `VALIDATION_ERROR` | 400 `{"code":"VALIDATION_ERROR","message":"description: 个数必须在0和500之间"}` | 通过 |
| N11 | name 空串 | `POST /api/locations` `{"name":"",...}` | 400 `VALIDATION_ERROR` | 400 `{"code":"VALIDATION_ERROR","message":"name: 不能为空"}` | 通过 |
| N12 | imageUrl 256 字 | `POST /api/locations`（`'u'×256`） | 400 `VALIDATION_ERROR` | 400 `{"code":"VALIDATION_ERROR","message":"imageUrl: 个数必须在0和255之间"}` | 通过 |
| N13 | description 恰好 500 字（边界） | `POST /api/locations`（`'b'×500`） | 201 且回显 500 字 | 201，`id=3`，descLen=500 | 通过 |
| N14 | imageUrl 恰好 255 字（边界） | `POST /api/locations`（`'v'×255`） | 201 且回显 255 字 | 201，`id=4`，imgLen=255 | 通过 |
| N15 | PUT 侧 description 501 字 | `PUT /api/locations/1` | 400 `VALIDATION_ERROR` | 400，code=`VALIDATION_ERROR` | 通过 |

### 9.5 图片链路用例明细（M）

| 编号 | 用例 | 请求 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- | --- | --- |
| M01 | 上传 1×1 合法 PNG（89 50 4E 47 魔数，119 B） | `POST /api/files`（multipart `file`，`type=image/png`） | 200 `{url}` | 200，`{"url":"/uploads/202609/264b1698-7d0d-461b-835e-410550d88b73.png"}` | 通过 |
| M02 | 用上传 url 创建位置 | `POST /api/locations` `imageUrl=<url>` | 201 回显 url | 201，`id=5`，urlEq=True | 通过 |
| M03 | 详情回读 | `GET /api/locations/5` | 200 与上传 url 一致 | 200，urlEq=True | 通过 |
| M04 | 树形回读 | `GET /api/locations` | 200 节点 url 一致 | 200，found/urlEq=True | 通过 |
| M05 | PUT 回填到另一位置 | `PUT /api/locations/2` `imageUrl=<url>` | 200 回显 | 200，urlEq=True | 通过 |
| M06 | PUT 后详情回读 | `GET /api/locations/2` | 200 与 url 一致 | 200，urlEq=True | 通过 |

### 9.6 既有行为回归用例明细（R）

| 编号 | 用例 | 请求 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- | --- | --- |
| R01 | 创建根位置 | `POST /api/locations` `parentId=null` | 201，parentId=null | 201，`id=6`，parentNull=True | 通过 |
| R02 | 创建子位置（根 → 子） | `POST /api/locations` `parentId=6` | 201，parentId=6 | 201，`id=7`，parentId=6 | 通过 |
| R03 | 树形 children 嵌套 / 路径展示 | `GET /api/locations` | 子节点在根的 `children[]` 内、不出现在顶层、parentId 正确 | 200，rootFound=True，childInRootChildren=True，childAtTop=False，childParentId=6 | 通过 |
| R04 | 详情 | `GET /api/locations/7` | 200 name/parentId 正确 | 200，name=回归子位置，parentId=6 | 通过 |
| R05 | 父位置不存在 | `POST /api/locations` `parentId=999999` | 404 `LOCATION_NOT_FOUND` | 404，code=`LOCATION_NOT_FOUND` | 通过 |
| R06 | 详情不存在 | `GET /api/locations/999999` | 404 `LOCATION_NOT_FOUND` | 404，code=`LOCATION_NOT_FOUND` | 通过 |
| R07 | 挂到自身 | `PUT /api/locations/6` `parentId=6` | 400 `LOCATION_CYCLE` | 400，code=`LOCATION_CYCLE` | 通过 |
| R08 | 挂到子孙 | `PUT /api/locations/6` `parentId=7` | 400 `LOCATION_CYCLE` | 400，code=`LOCATION_CYCLE` | 通过 |
| R09 | 有物品时删除 | `DELETE /api/locations/7`（位置下有物品 itemId=1） | 400 `LOCATION_IN_USE` | 400 `{"code":"LOCATION_IN_USE","message":"该位置下仍有物品，请先迁移或删除物品"}` | 通过 |
| R10 | 有子级时删除 | `DELETE /api/locations/6` | 400 `LOCATION_HAS_CHILDREN` | 400，code=`LOCATION_HAS_CHILDREN` | 通过 |
| R11 | 解除占用（前置） | `DELETE /api/items/1` | 204 | 204 | 通过 |
| R12 | 正常删除 | `DELETE /api/locations/7` | 204 | 204 | 通过 |
| R13 | 删除后再查详情 | `GET /api/locations/7` | 404 `LOCATION_NOT_FOUND` | 404，code=`LOCATION_NOT_FOUND` | 通过 |
| R14 | 删除不存在 | `DELETE /api/locations/999999` | 404 `LOCATION_NOT_FOUND` | 404，code=`LOCATION_NOT_FOUND` | 通过 |
| R15 | 删除根位置（子已删） | `DELETE /api/locations/6` | 204 | 204 | 通过 |

> 前置数据（不计入用例）：`POST /api/categories` → 201（id=1）、`POST /api/items`（绑定子位置）→ 201（id=1），证据 `evidence-loc\R-PREREQ.res.txt`。

### 9.7 失败明细 / 缺陷清单

**无。** 本轮 37/37 全部通过，未发现新缺陷；本轮亦未复现历史缺陷 NEW-D1（死锁 → 500，属 items 库存并发域，不在本轮范围）。

### 9.8 覆盖缺口

1. **静态资源可访问性**：契约 §5.6 记载 `/uploads/**` 由 nginx 托管，本环境无 nginx，未验证 `GET /uploads/...` 是否 200；`imageUrl` 字段的**存取一致性**已覆盖（M01–M06），资源本身可达性 → **未知/需确认**。
2. **description 长度计数语义**：实现为 Bean Validation `@Size(max=500)`（按 Java String 长度 = UTF-16 code unit 计数）。本轮以 ASCII `501/500` 验证边界；**中文 501 字、emoji（代理对，按 2 计）未单列用例** → 若产品语义为「500 个汉字 / 500 个字符（按 code point）」，需契约明确后再补测。
3. **契约外的实现级约束**：DTO 另有 `name @Size(max=50)`，契约 §5.3 未写明 `name` 长度上限，本轮未作为用例（如需纳入契约请 architect 确认）。
4. **并发 / 幂等**：契约未定义 locations 并发写语义，未做并发测试。
5. **401/403 权限分支**：沿用全局安全配置（前几轮已验证无 token 401），本轮未重复。

### 9.9 安全扫描（本轮）

| 检查项 | 结果 |
| --- | --- |
| CWE-798 硬编码凭据 | CLEAR（测试账号为本轮在隔离库自建的临时 ADMIN，非生产凭据；未写入生产配置） |
| CWE-200 敏感信息落盘 | CLEAR（本报告不含 token / Authorization 头；token 仅存在于临时目录的登录响应文件与脚本运行时变量） |
| CWE-89 / CWE-78 注入 | CLEAR（参数经 JSON body / URL 传递，无 SQL、Shell 拼接） |
| CWE-22 路径穿越 | CLEAR（本轮仅上传，未触发删除路径；删除路径已在 §7.3 R01–R05 覆盖） |

### 9.10 结论

- **新字段链路**：树形与详情均返回 `description` / `imageUrl`（无值时键存在且为 `null`）、POST 写入回显、PUT 更新与置 `null` 清空、501 字 / 空 name / 256 字 imageUrl 的 400 `VALIDATION_ERROR`、500/255 字边界 201 —— **全部通过**。
- **图片链路**：`POST /api/files` 上传 → 返回 `/uploads/yyyyMM/uuid.png` → 作为 `imageUrl` 保存 → 树形/详情/PUT 后回读一致 —— **全部通过**。
- **既有行为回归**：树形嵌套与路径、详情、404 `LOCATION_NOT_FOUND`（父不存在/详情不存在/删除不存在）、400 `LOCATION_CYCLE`（自身/子孙）、400 `LOCATION_IN_USE`、400 `LOCATION_HAS_CHILDREN`、正常删除 204 —— **全部通过，无回归**。
- **Status=API_PASS（本轮 37/37，0 缺陷）**；报告可复现命令：`powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\Administrator\AppData\Local\Temp\opencode\api-test\run-tests-loc.ps1`（需后端在 8080 运行；重复执行前须清空 locations/categories 数据或按 9.2 调整 A00/N01）。。

---

## 十、code-review 增量（Y4 imageUrl 格式校验，2026-09-28）

### 10.1 背景 / 目标 / 环境

- **Y4（后端新增校验）**：`LocationRequest.imageUrl` 增加 `@Pattern("(^$|^/(?!/)[A-Za-z0-9._/-]{1,254}$)")`，只允许空值或站内单斜杠路径，拒绝 `http(s)://` 外链与 `//host/...` 协议相对 URL，违反 → 400 `VALIDATION_ERROR`；契约见 `docs/api-spec.md` §5.3 图片口径注记（code-review Y3/Y4）。
- **Y1（前端文件生命周期，接口层影响）**：前端改为「保存成功 / 取消时才调 `DELETE /api/files/{name}`」，接口本身无改动；本轮只做该端点**冒烟**，确认对契约口径的正常路径仍可用。
- **环境**：后端 `http://localhost:8080`（新 jar 含 @Pattern，java 进程 2026-09-28 17:22:36/37 启动，**本轮未重启**），隔离库 `family_storage_e2e`；注册已关闭，使用既有 ADMIN 账号，凭据经环境变量 `API_TEST_USER` / `API_TEST_PASS` 注入（报告与脚本不落盘明文，CWE-798）。
- **既有数据（测试前快照，禁止触碰）**：位置 `id=3`「厨房橱柜」（子 `id=4`「吊柜」，`parentId=3`）、分类 `id=1/2`、物品 `id=2/3/4`（均挂 `locationId=4`）。
- **执行时间（最终有效轮）**：2026-09-28 17:58:41。
- **脚本 / 结果 / 证据**：
  - 脚本：`C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-y4\run-tests-y4.ps1`（UTF-8 BOM）
  - 结果：同目录 `results-y4.json`（逐用例请求体/响应体/断言）、`summary-y4.txt`（摘要）
  - 逐用例证据：`evidence\{用例编号}.req.json` + `evidence\{用例编号}.res.txt`，共 73 个文件
  - 磁盘核验：上传根 `D:\xcode\wt\.worktrees\family-storage-system\backend\uploads\202609\`

### 10.2 执行过程说明（测试工具问题，非产品缺陷）

1. **第 1 轮**：脚本把「用例编号」写进结果对象的 `id` 字段，覆盖了响应 `id`，导致依赖创建结果的请求全部打到 `/api/locations/-1`（A05/A12/A13/B03–B08、S03/S05 连带失败），收尾 V01 报 8 个 `Y4T*` 自建节点残留 → **测试脚本缺陷**，已修正；残留 8 个位置（id 19–26）当轮已全部手工删除，树恢复 `3/4`。
2. **第 2 轮**：`B11` 删除名取值 bug（`uploads/202609/...` 而非契约的 `202609/...`），返回 204 但**磁盘文件未删**；该轮自建位置已清理，遗留上传文件 `29bf27f1-*.png`、`bb873301-*.png` 已用正确相对路径删除并核验磁盘消失。
3. 两轮问题均为**测试工具/脚本问题，不计入产品缺陷**；修正后第 3 轮为最终有效轮（下方统计与明细均来自该轮），并新增磁盘级断言（上传后 `onDisk=True`、删除后 `fileGone=True`），避免仅凭 204 判定。

### 10.3 总体统计（最终有效轮）

| 指标 | 数值 |
| --- | --- |
| 用例总数 | **37**（主用例 32 + 前置数据 5） |
| 通过 | **37（100%）** |
| 失败 | **0** |
| 新发现缺陷 | **0**（另有 2 条观测/建议，见 10.9） |
| 请求耗时 | min 3.8 ms / max 64.4 ms（登录）/ p95 20.9 ms，Σ348.1 ms，全部 < 500 ms 阈值 |

| 分组 | 编号 | 用例数 | 通过 | 失败 |
| --- | --- | --- | --- | --- |
| P 认证 | P00 | 1 | 1 | 0 |
| A Y4 `imageUrl` @Pattern 校验 | A01–A14 | 14 | 14 | 0 |
| B 回归冒烟（含 Y1 文件冒烟） | B01–B11 | 11 | 11 | 0 |
| X `DELETE /api/files` 路径口径观测 | X01–X03 | 3 | 3 | 0 |
| V 既有数据完整性 | V01–V03 | 3 | 3 | 0 |
| S 前置数据（不计入主统计口径亦全部通过） | S01–S05 | 5 | 5 | 0 |

### 10.4 A 组：Y4 `imageUrl` 校验明细（14 条，全部通过）

| 编号 | 用例 | 请求（节选） | 期望 | 实际 | 耗时 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| A01 | POST 外链 `https://` | `POST /api/locations` `imageUrl="https://evil.example/a.png"` | 400 `VALIDATION_ERROR` | 400 `{"code":"VALIDATION_ERROR","message":"imageUrl: imageUrl 须为 /api/files 返回的站内路径（如 /uploads/202609/uuid.jpg）"}` | 4.7ms | 通过 |
| A02 | POST 协议相对 `//` | `imageUrl="//evil.example/a.png"` | 400 | 400，同上 message（含 `imageUrl` 字段名） | 4.7ms | 通过 |
| A03 | PUT 外链 `https://` | `PUT /api/locations/45`（自建 Y4T-CX） | 400 | 400，`code=VALIDATION_ERROR` | 5.4ms | 通过 |
| A04 | PUT 协议相对 `//` | `PUT /api/locations/45` `imageUrl="//evil.example/a.png"` | 400 | 400，`code=VALIDATION_ERROR` | 5.5ms | 通过 |
| A05 | 校验失败无部分写入 | `GET /api/locations/45`（A03/A04 之后） | 200，`imageUrl` 未被污染 | 200，`imageUrl=""`、`description="y4 control node"` 保持原值 | 8.1ms | 通过 |
| A06 | 合法站内路径 | `imageUrl="/uploads/202609/real.png"` | 201 回显一致 | 201，回显 `/uploads/202609/real.png`（`id=46`） | 13.0ms | 通过 |
| A07 | 空串放行 | `imageUrl=""` | 201，回显 `""` 或 `null` | 201，回显 **`""`**（空串按原值存取，非 null；`id=47`） | 6.9ms | 通过 |
| A08 | 相对穿越 `../../etc/passwd` | `imageUrl="../../etc/passwd"` | 400 | 400 `VALIDATION_ERROR`（不以 `/` 开头，正则拒绝） | 5.5ms | 通过 |
| A09 | 含空格 `/a b.png` | `imageUrl="/a b.png"` | 400 | 400 `VALIDATION_ERROR` | 5.4ms | 通过 |
| A10 | description 500 字边界 | `description='a'*500` | 201 且回显 500 字 | 201，`descLen=500`（`id=48`） | 6.9ms | 通过 |
| A11 | description 501 字 | `description='a'*501` | 400 `VALIDATION_ERROR` | 400 `{"code":"VALIDATION_ERROR","message":"description: 个数必须在0和500之间"}` | 5.0ms | 通过 |
| A12 | PUT 合法站内路径（正向对照） | `PUT /api/locations/46` `imageUrl="/uploads/202609/ok.png"` | 200 回显一致 | 200，回显 `/uploads/202609/ok.png` | 9.4ms | 通过 |
| A13 | PUT 空串 | `PUT /api/locations/46` `imageUrl=""` | 200，回显 `""`/`null` | 200，回显 **`""`** | 30.6ms | 通过 |
| A14 | **观测**：`/../etc/passwd` | `imageUrl="/../etc/passwd"` | 记录实际（契约正则字面允许） | **201 被接受并回显**（`id=49`），见 10.9 OBS-1 | 6.8ms | 通过（观测） |

> 断言口径：除状态码外，逐条校验响应体 `code` 与 `message` 是否含触发字段名（`imageUrl` / `description`），成功用例校验回显值与请求一致；证据 `evidence\A01–A14.req.json/.res.txt`。

### 10.5 B 组：回归冒烟明细（11 条，全部通过）

| 编号 | 用例 | 请求 | 期望 | 实际 | 耗时 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| B01 | 树形含新字段 + 嵌套 | `GET /api/locations` | 200，节点含 `description`/`imageUrl` 键，3 有子 4、4 不在顶层 | 200，`roots=6 id3=True child4=True top4=0 descKey=True imgKey=True`（当时含自建节点） | 20.9ms | 通过 |
| B02 | 带中文说明创建 | `POST /api/locations` `description="Y4 回归说明：吊柜放干货"` | 201 回显 | 201，回显一致（`id=50`） | 7.0ms | 通过 |
| B03 | PUT 改说明 | `PUT /api/locations/50` | 200 回显新值 | 200，`description="Y4 更新后的说明"` | 8.4ms | 通过 |
| B04 | PUT 置 `null` 清空 | `PUT /api/locations/50` `description=null` | 200 回显 null | 200，`desc=null` | 9.1ms | 通过 |
| B05 | GET 回读 | `GET /api/locations/50` | 200 `description=null` 且 name 不变 | 200，`desc=null name=Y4T-B02` | 6.9ms | 通过 |
| B06 | 删除保护：有物品 | `DELETE /api/locations/51`（自建位置 + 自建物品 `id=7`） | 400 `LOCATION_IN_USE` | 400 `{"code":"LOCATION_IN_USE","message":"该位置下仍有物品，请先迁移或删除物品"}` | 5.3ms | 通过 |
| B07 | 删除保护：有子级 | `DELETE /api/locations/52`（自建父 52 + 子 53） | 400 `LOCATION_HAS_CHILDREN` | 400 `{"code":"LOCATION_HAS_CHILDREN","message":"该位置下仍有子位置，请先删除子位置"}` | 6.3ms | 通过 |
| B08 | 正常删除自建节点 | `DELETE /api/locations/47` | 204 且空体 | 204，`bodyLen=0` | 9.1ms | 通过 |
| B09 | 删除后详情 | `GET /api/locations/47` | 404 `LOCATION_NOT_FOUND` | 404，`code=LOCATION_NOT_FOUND` | 5.8ms | 通过 |
| B10 | 真实 1×1 PNG 上传 | `POST /api/files`（multipart `file`，119B PNG） | 200 `{url}` 且**磁盘落盘** | 200，`url=/uploads/202609/f02d1bb9-…png`，`onDisk=True` | 8.0ms | 通过 |
| B11 | **Y1 冒烟**：删除文件 | `DELETE /api/files/202609/f02d1bb9-….png`（契约相对路径） | 204 且**磁盘文件消失** | 204，`bodyLen=0`，`fileGone=True` | 4.3ms | 通过 |

### 10.6 X 组：`DELETE /api/files/{name}` 路径口径观测（3 条，全部通过）

| 编号 | 用例 | 请求 | 期望 | 实际 | 耗时 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| X01 | 前置：第二张上传 | `POST /api/files` | 200 且落盘 | 200，`url=/uploads/202609/7f715cfa-….png`，`onDisk=True` | 7.8ms | 通过 |
| X02 | **观测**：非契约删除名 | `DELETE /api/files/uploads/202609/7f715cfa-….png`（多带 `uploads/` 前缀） | 记录状态码与磁盘状态 | **204 但 `stillOnDisk=True`（静默 no-op）**，见 10.9 OBS-2 | 3.8ms | 通过（观测） |
| X03 | 契约删除名收尾 | `DELETE /api/files/202609/7f715cfa-….png` | 204 且文件消失 | 204，`fileGone=True` | 4.4ms | 通过 |

> 契约 §5.6：`name` 为**相对路径**（如 `202609/uuid.png`）；前端 `frontend/src/api/files.ts` 的 `toFileDeleteName()` 已正确剥掉 `/uploads/` 前缀（`/uploads/202609/x.png` → `202609/x.png`），故 Y1 前端链路口径正确，X02 仅为「传错路径时的行为」观测。

### 10.7 V 组：既有数据完整性 + S 前置数据

| 编号 | 用例 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- | --- |
| V01 | 收尾树 = 起始快照 | 节点集合、名称完全一致，且无 `Y4T*` 残留 | 200，`pre=2 post=2 leftoverY4=0 diff=`（`id=3`「厨房橱柜」+ 子 `id=4`「吊柜」原样保留） | 通过 |
| V02 | 物品数回到起始 | `totalElements=3` | 200，`total=3 pre=3`（`id=2/3/4` 未动） | 通过 |
| V03 | 分类不变 | `ids=1,2` | 200，`ids=1,2` | 通过 |

前置数据（S01–S05，5/5 通过，均已回收）：`Y4T-CX`(45)、`Y4T-A06`(46)、`Y4T-A07`(47，B08 已删)、`Y4T-A10`(48)、`Y4T-A14`(49)、`Y4T-B02`(50)、`Y4T-INUSE`(51)、`Y4T-PARENT`(52)、`Y4T-CHILD`(53)、物品 `id=7`，以及本轮 2 张上传文件——**全部删除**：位置/物品逐个返回 204（B08 目标为 404 属预期），文件经磁盘核验 `f02d1bb9-*`、`7f715cfa-*` 均 `Test-Path=False`；连同 10.2 中两轮遗留文件也已清理，`uploads/202609` 下无本轮残留。

### 10.8 失败明细 / 缺陷清单

**无。** 最终有效轮 37/37 全部通过，**未发现新缺陷**；Y4 与 Y1 的接口层验证目标全部达成，且未对既有功能（树形、说明字段、删除保护、文件上传删除）造成回归。

### 10.9 观测与建议（不计入缺陷，需 architect 确认）

| 编号 | 级别 | 观测 | 依据 / 影响 | 建议 |
| --- | --- | --- | --- | --- |
| OBS-1 | 低（契约一致性） | `imageUrl="/../etc/passwd"` **通过 @Pattern → 201 被存储**（A14） | 与 §5.3 注记正则 `^/(?!/)[A-Za-z0-9._/-]{1,254}$` **字面一致**（`.`、`/` 均在字符类内），故**不违反契约**；`imageUrl` 仅作前端 `<img src>` 展示，服务端不据此读文件，且 `..` 段无法穿透 nginx 静态根 → 实际风险低 | 如需彻底杜绝 `..` 段，请 architect 更新契约正则（如排除 `..` 路径段）并由 backend 同步；否则维持现状并在契约注记中说明 |
| OBS-2 | 低（可观测性） | `DELETE /api/files/uploads/202609/x.png`（多带 `uploads/` 前缀，非契约路径）返回 **204 但文件未删**（X02） | 契约 §5.6 只定义 `202609/uuid.png`，且**未定义目标不存在时的状态码** → 标注**未知/需确认**；`FileStorageService.delete` 用 `Files.deleteIfExists` + 相对路径拼接，故对不存在目标静默 204。前端 `toFileDeleteName()` 口径正确，Y1 链路不受影响 | architect 明确「目标不存在」语义（幂等 204 vs 404 `FILE_NOT_FOUND`）；若保留 204，建议契约注明「204 不代表文件确已存在」 |

### 10.10 覆盖缺口

1. **静态资源可达性**：无 nginx，`GET /uploads/**` 是否 200 未验证 → **未知/需确认**（同 §9.8 缺口 1）。本轮改用**磁盘文件存在性**作为删除生效的替代证据。
2. **`imageUrl` 与真实文件存在性的一致性**：A06 传 `/uploads/202609/real.png`（不存在的文件）按契约仅做格式校验即 201，未校验存在性 —— 与契约口径一致，不作为缺陷；如需强一致需契约新增校验规则。
3. **401/403 分支**：沿用全局安全配置（前几轮已验证），本轮未重复。
4. **description 计数语义**（中文/emoji 按 code point 还是 UTF-16）：同 §9.8 缺口 2，本轮仍以 ASCII 500/501 验证边界，未新增中文边界用例。
5. **PUT 侧 description 501 字**：§9.4 N15 已覆盖，本轮仅重测 POST 侧（A11）与 PUT 侧 `imageUrl`（A03/A04/A12/A13）。
6. **并发 / 幂等**：契约未定义 locations 并发写语义，未测；`DELETE /api/files` 对同一路径重复调用均返回 204（幂等表现），未做并发删除。

### 10.11 安全扫描（本轮）

| 检查项 | 结果 |
| --- | --- |
| CWE-798 硬编码凭据 | CLEAR（登录凭据经 `API_TEST_USER`/`API_TEST_PASS` 环境变量注入；报告与脚本不含密码；token 仅存在于临时目录登录响应与运行时变量） |
| CWE-200 敏感信息落盘 | CLEAR（本报告不含 token / Authorization 头 / 密码） |
| CWE-89 / CWE-78 注入 | CLEAR（参数经 JSON body / multipart / URL 路径传递，无 SQL、Shell 拼接） |
| CWE-22 路径穿越 | CLEAR（A08 `../../etc/passwd`、A09 含空格路径被 400 拒绝；A14 见 OBS-1；文件删除侧穿越行为见 §7.3，本轮 X 组未触发穿越样本） |
| CWE-79（报告 XSS） | CLEAR（报告为 Markdown 纯文本，响应原文按代码块呈现，未做 HTML 渲染注入） |

### 10.12 结论与复现

- **Y4 生效**：POST 侧 4 类非法 `imageUrl`（`https://` 外链、`//` 协议相对、`../../etc/passwd` 穿越、含空格 `/a b.png`）与 PUT 侧 2 类（`https://`、`//`）**全部返回 400 `VALIDATION_ERROR`**（message 精确指向 `imageUrl`，共 6 条用例）；合法站内路径 POST 201 / PUT 200 回显一致；空串放行并回显 `""`；校验失败不产生部分写入（A05）。
- **未干扰既有校验**：`description` 500 字 → 201、501 字 → 400 `VALIDATION_ERROR`（`@Size` 边界完好），PUT 正向链路 200 正常。
- **Y1 冒烟**：`POST /api/files` 上传真实 PNG → `DELETE /api/files/202609/{uuid}.png` → **204 且磁盘文件消失**；错误前缀路径的行为见 OBS-2（前端口径正确）。
- **回归无破坏**：树形含 `description`/`imageUrl`、说明字段增改清回读、删除保护三条（`LOCATION_IN_USE` / `LOCATION_HAS_CHILDREN` / 204）、文件上传删除全通过；**既有 id=3/4、分类 1/2、物品 2/3/4 未被触碰**（V01–V03）。
- **Status=API_PASS（37/37，0 缺陷，2 条观测建议 OBS-1/OBS-2 待 architect 确认）**。

**复现命令**（需后端在 8080 运行；会自建并自清理 `Y4T*` 位置与测试图片，不触碰 id 3/4）：

```powershell
$env:API_TEST_USER = '<ADMIN 用户名>'
$env:API_TEST_PASS = '<对应密码>'
powershell -NoProfile -ExecutionPolicy Bypass -File `
  C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-y4\run-tests-y4.ps1
# 退出码 0 = 全通过；结果见同目录 results-y4.json / summary-y4.txt / evidence\
```

单条用例手工复现样例（Y4 核心）：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<user>","password":"<pass>"}' | jq -r .token)

# 期望 400 VALIDATION_ERROR
curl -s -o - -w '\n%{http_code}\n' -X POST http://localhost:8080/api/locations \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Y4T-A01","parentId":null,"sortOrder":1,"description":null,"imageUrl":"https://evil.example/a.png"}'

# 期望 201 且回显一致
curl -s -o - -w '\n%{http_code}\n' -X POST http://localhost:8080/api/locations \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Y4T-A06","parentId":null,"sortOrder":91,"description":null,"imageUrl":"/uploads/202609/real.png"}'

# Y1 冒烟：期望 204 且磁盘文件消失（name=202609/<uuid>.png）
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE \
  http://localhost:8080/api/files/202609/<uuid>.png -H "Authorization: Bearer $TOKEN"
```

### 10.13 OBS-1 收紧复测（2026-09-28）

**背景**：§10.9 OBS-1 记录 `imageUrl="/../etc/passwd"` 曾通过 @Pattern → **201 被存储**（A14）。architect 采纳收紧建议，`LocationRequest.imageUrl`（`backend/src/main/java/com/family/storage/location/dto/LocationRequest.java:17`）正则更新为：

```
(^$|^/(?!/)(?!.*\.\.)[A-Za-z0-9._/-]{1,254}$)
```

即在原正则基础上新增 `(?!.*\.\.)`，**拒绝任何含 `..` 的路径段**；契约 `docs/api-spec.md` §5.3 图片口径注记已同步为同一正则。backend 已重启（新 jar 生效）。本轮为收尾微型增量测试（6 条用例），**未重启服务**。

**环境**：`http://localhost:8080`，库 `family_storage_e2e`；ADMIN 凭据经 `API_TEST_USER` / `API_TEST_PASS` **环境变量**注入（脚本与报告均不含密码，登录证据即时删除）。脚本 `C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-y4\run-obs1-retest.ps1`，证据 `evidence-obs1\`，机读结果 `results-obs1.json`。

**前置**：`GET /api/locations` 快照 `pre=[3]`（`id=3`「厨房橱柜」+ 子 `id=4`「吊柜」，原样不动）；setup S1 自建对照节点 `OBS1-CTL`（`id=54`，`imageUrl=""`、`description="obs1 control"`）供 T03 PUT 使用。

#### 10.13.1 用例明细（6 条用例 + 1 条无部分写入辅助断言，全部通过）

| 编号 | 用例 | 请求 | 期望 | 实际 | 耗时 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| T01 | POST `imageUrl="/../etc/passwd"`（**原 OBS-1 样本**） | `POST /api/locations` `{"name":"OBS1-R1","parentId":null,"sortOrder":92,"description":null,"imageUrl":"/../etc/passwd"}` | 400 `VALIDATION_ERROR` | 400，`{"code":"VALIDATION_ERROR","message":"imageUrl: imageUrl 须为 /api/files 返回的站内路径（如 /uploads/202609/uuid.jpg）"}` | 62.8ms | 通过 |
| T02 | POST 中段穿越 `/a/../b.png` | `POST /api/locations` `imageUrl="/a/../b.png"` | 400 `VALIDATION_ERROR` | 400，同上 message（含字段名 `imageUrl`） | 54.6ms | 通过 |
| T03 | PUT 多段穿越 `/x/../../etc/passwd` | `PUT /api/locations/54` `{"name":"OBS1-CTL","parentId":null,"sortOrder":91,"description":"obs1 control","imageUrl":"/x/../../etc/passwd"}` | 400 `VALIDATION_ERROR` | 400，同上 message | 55.4ms | 通过 |
| T03b | 校验失败无部分写入（辅助断言） | `GET /api/locations/54`（T03 之后） | 200，`imageUrl`/`description` 未被污染 | 200，`imageUrl=""`、`description="obs1 control"` **均保持原值** | 64.8ms | 通过 |
| T04 | 合法站内路径仍放行 | `POST /api/locations` `imageUrl="/uploads/202609/11111111-2222-3333-4444-555555555555.png"` | 201 且回显一致 | 201，`id=55`，`imageUrl` 回显与请求**逐字一致** | 56.7ms | 通过 |
| T05 | 空串仍放行 | `POST /api/locations` `imageUrl=""` | 201 | 201，`id=56`，回显 `""`（空串按原值存取，非 null，与 10.4 A07 口径一致） | 54.7ms | 通过 |
| T06 | 单点文件名不被 `..` 规则误伤 | `POST /api/locations` `imageUrl="/uploads/202609/.hidden.png"` | 201 且回显一致 | 201，`id=57`，`imageUrl` 回显 `/uploads/202609/.hidden.png` | 55.6ms | 通过 |

> 断言口径：400 用例除状态码外，逐条校验响应体 `code == VALIDATION_ERROR` 且 `message` 含触发字段名 `imageUrl`；201 用例校验 `imageUrl` 回显与请求**逐字相等**。证据 `evidence-obs1\T01–T06.req.json/.res.json`（另 `S1.*` 前置、`PRE/POST-SNAP.*` 快照、`DEL-54..57.*` 清理）。

#### 10.13.2 统计

| 指标 | 值 |
| --- | --- |
| 用例总数 | 6（+1 条无部分写入辅助断言 T03b，共 7 条请求断言） |
| 通过 / 失败 | **6/6 通过**（含 T03b 共 7/7），**0 失败** |
| 拒绝类（400） / 放行类（201） | 3 / 3 |
| 用例耗时 | avg **57.8ms**，p95 **62.8ms**，min 54.6ms，max 64.8ms（全部 < 500ms 阈值） |
| 全流程请求（13 次，含登录/快照/清理） | avg 68.1ms，p95 102.7ms |
| 数据清理 | **OK**：自建 `OBS1-CTL`(54)、`OBS1-R4`(55)、`OBS1-R5`(56)、`OBS1-R6`(57) 全部 204 删除，收尾树 `post=[3]` 与 `pre` **完全一致**；`id=3/4` 未被触碰；未上传任何文件，磁盘无残留 |

#### 10.13.3 失败明细 / 缺陷清单

**无。** 6/6 全部通过，本轮**未发现新缺陷**，也未引入回归（拒绝类 3 条全部按新正则返回 400，放行类 3 条全部按原口径 201）。

#### 10.13.4 观测与结论

- **OBS-1 关闭**：原观测样本 `/../etc/passwd`（A14 曾 201）现**稳定返回 400 VALIDATION_ERROR**；中段穿越 `/a/../b.png` 与 PUT 侧 `/x/../../etc/passwd` 同样 400 —— **收紧在 POST/PUT 两侧均生效**。
- **正则未过度收紧（无误伤）**：合法上传路径（T04）、空串（T05）、**单点隐藏文件名 `.hidden.png`（T06）** 全部放行并逐字回显，`(?!.*\.\.)` 只拦截连续两点，`.` 仍在字符类内。
- **校验失败不落库**：T03b 证明 PUT 校验失败后 `imageUrl` / `description` 均保持原值（与 10.4 A05 口径一致）。
- **契约与实现一致**：`docs/api-spec.md` §5.3 注记正则与 `LocationRequest.java:17` 逐字相同，无「契约-实现漂移」。
- **Status=API_PASS（6/6，0 缺陷，OBS-1 由「建议 architect 确认」变更为「已修复并复测通过」）。**

#### 10.13.5 覆盖缺口（本轮未测）

1. **穿越样本的其他形态**：URL 编码 `%2e%2e`、反斜杠 `..\`、单段 `..` 结尾（如 `/uploads/..`）、Windows 盘符路径 —— 未实测；按新正则字符类 `[A-Za-z0-9._/-]`（无 `%`、`\`、`:`）**推断会被拒，但未验证** → 标注**未知/需确认**，如需强保证建议补测。
2. **含 `..` 的合法文件名被一并拒绝**（如假设存在 `a..b.png`）：新规则为「全路径不得含 `..`」的粗粒度拒绝，属 architect 收紧时的已知取舍；uuid 文件名不含 `..`，实际无影响，**不作为缺陷**。
3. **边界长度**：255/256 字 imageUrl 边界沿用 §9.4 N12/N14，本轮未重复；`imageUrl` 与真实文件存在性一致性同 10.10 缺口 2，未变。
4. **401/403 分支 / description 中文计数**：同 10.10 缺口 3、4，未重复。

#### 10.13.6 安全扫描（本轮）

| 检查项 | 结果 |
| --- | --- |
| CWE-798 硬编码凭据 | CLEAR（凭据仅经 `API_TEST_USER`/`API_TEST_PASS` 环境变量注入；脚本与本报告无密码；登录请求/响应证据运行后即删除） |
| CWE-200 敏感信息落盘 | CLEAR（`results-obs1.json` / 证据文件不含 token、Authorization 头或密码；本报告仅含状态码与响应体业务字段） |
| CWE-22 路径穿越 | **CLOSED**（T01/T02/T03 三个穿越样本均被 400 拒绝并**不落库**；其余形态见 10.13.5 缺口 1） |
| CWE-89 / CWE-78 注入 | CLEAR（参数仅经 JSON body 传递，无 SQL/Shell 拼接） |
| CWE-79（报告 XSS） | CLEAR（报告为 Markdown 纯文本，响应原文按代码块呈现） |

#### 10.13.7 复现

```powershell
$env:API_TEST_USER = '<ADMIN 用户名>'
$env:API_TEST_PASS = '<对应密码>'
powershell -NoProfile -ExecutionPolicy Bypass -File `
  C:\Users\Administrator\AppData\Local\Temp\opencode\api-test-y4\run-obs1-retest.ps1
# 退出码 0 = 6/6 通过且数据已自清理；结果 results-obs1.json、证据 evidence-obs1\
```

单条手工复现（期望 **400**；T04 同请求改 `imageUrl` 为合法路径则期望 **201**）：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<user>","password":"<pass>"}' | jq -r .token)

# 期望 400 VALIDATION_ERROR（原 OBS-1 样本）
curl -s -o - -w '\n%{http_code}\n' -X POST http://localhost:8080/api/locations \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"OBS1-R1","parentId":null,"sortOrder":92,"description":null,"imageUrl":"/../etc/passwd"}'

# 期望 400（PUT 侧，<id> 为任一自建位置）
curl -s -o - -w '\n%{http_code}\n' -X PUT http://localhost:8080/api/locations/<id> \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"OBS1-CTL","parentId":null,"sortOrder":91,"description":"obs1 control","imageUrl":"/x/../../etc/passwd"}'

# 期望 201（单点文件名不误伤）
curl -s -o - -w '\n%{http_code}\n' -X POST http://localhost:8080/api/locations \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"OBS1-R6","parentId":null,"sortOrder":96,"description":null,"imageUrl":"/uploads/202609/.hidden.png"}'
```
