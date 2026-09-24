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