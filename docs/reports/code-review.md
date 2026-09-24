# 代码质量与安全评审报告 — 家庭收纳物品管理系统

- 评审人：code-reviewer（子 Agent）
- 评审基线：worktree `family-storage-system`，HEAD `0273c5b`（branch `feat/family-storage-system`）
- 评审范围：
  - 后端 `backend/src/main/java/`（auth/user/category/location/item/stock/shopping/file/notification/settings/statistics/common/config）+ `resources/`（application*.yml、db/migration/V1__init.sql）
  - 前端 `frontend/src/`（api/store/views/components/router/types）+ `vite.config.ts`、`package.json`
  - 部署件 `deploy/`、`.env.example`、`.gitignore`
  - 契约 `docs/superpowers/specs/2026-09-24-家庭收纳管理系统-design.md` §5
- 测试证据：58 后端单测（含 D1/D2/D3 回归）、api-tester 79/79、前端 vitest 20、Playwright E2E PASS（46 步截图证据）

---

## 一、评审摘要

| 严重度 | 数量 | 必修项 |
| --- | --- | --- |
| HIGH | 1 | 生产 JWT 密钥存在公开兜底值（应用仍可带弱密钥启动） |
| MEDIUM | 7 | 见 §2.2 明细，以上线前建议处理 |
| LOW / 建议 | 13 | 见 §2.3，可择期处理 |

结论：**有条件通过（APPROVED_WITH_NOTES）**。整体契约一致性、鉴权分层、文件上传防穿越、通知幂等、前后端错误码对齐均达到可交付质量；1 项 HIGH 必须在上线前修复（去除 JWT 兜底值），7 项 MEDIUM 建议上线前评估处理。

> 历史 api-tester 报告（HEAD c3aa12b）标注的 F05 文件删除 400、E02 设置写入 500、F04 超限 500 三个缺陷，均已在本基线修复并有回归单测覆盖（`FileStorageServiceTest.deleteWithLeadingSlashStillDeletesStoredFile` 等），确认收敛。

---

## 二、问题清单

### 2.1 HIGH（必修）

**H1. 生产 JWT 密钥带公开兜底值，弱密钥可让攻击者伪造 ADMIN 令牌**
- 位置：`backend/src/main/resources/application-prod.yml:27`
- 问题：`secret: ${JWT_SECRET:prod-secret-replace-me-at-least-32-bytes}` 保留了默认可用的公开弱密钥。`deploy/start.sh:34` 虽强制要求 JWT_SECRET 非空，但任何绕过 start.sh 的启动方式（systemd 直启、`java -jar` 手工启动、Docker 未注入变量）都会**带着仓库已知密钥正常运行**。`JwtTokenProvider` 用该密钥签发/验签，攻击者可用公开密钥伪造任意 `subject=任意userId, claim=role=ADMIN` 的 token 完全接管系统。与 `DB_PASSWORD`（`application-prod.yml:10` 无兜底、缺失即拒启动）的处理不一致。
- 修复建议：改为无默认值 `${JWT_SECRET}`（缺失时启动失败，快速失败），与 DB_PASSWORD 一致；同时在 `deploy/start.sh` 增加长度校验（`${#JWT_SECRET} -ge 32`）。`application.yml`（dev）的 `dev-secret-change-me-at-least-32-bytes` 仅为开发兜底，可保留但注释强调不可用于生产。

### 2.2 MEDIUM（建议上线前处理）

**M1. 停用/删除用户后已签发 JWT 在 168h 内仍有效（越权窗口）**
- 位置：`JwtAuthFilter.java:29-41`、`AuthService.login:44-46`、`application.yml:24`（expire-hours: 168）
- 问题：登录时校验了 `enabled`，但此后每次请求仅解析 token 中的 userId/role 写入 SecurityContext，不查库校验用户是否仍启用/存在。管理员停用某成员后，该成员凭旧 token 在 7 天内仍可调用除 `/api/auth/login` 外的所有接口（含 `/api/auth/me`、`/api/auth/password`）。过期时间 168h 偏长进一步放大窗口。
- 修复建议（择一）：① `JwtAuthFilter` 解析出 userId 后查 `UserRepository` 校验存在且 enabled（成本低、立即生效）；② 至少将 token 过期时间缩短（如 12–24h）；③ 维护 token 黑名单/版本号（较重）。

**M2. 出入库并发存在丢失更新，库存与审计历史可能失真**
- 位置：`ItemService.adjustStock:127-140`
- 问题：读 `item.getQuantity()` → 计算 newQuantity → save，无锁、无 `@Version`。并发两次 OUT（或 OUT+ADJUST）会在同一存量上计算，后写覆盖先写，导致 `items.quantity` 与 `stock_changes` 累加值不一致、历史记录多于实际扣减（"超发"）。
- 修复建议：① 最小改动：`Item` 增加 `@Version` 乐观锁，`adjustStock` 捕获 `OptimisticLockingFailureException` 重试或回 409 `STOCK_CONFLICT`；② 严谨方案：`findById` 使用 `@Lock(LockModeType.PESSIMISTIC_WRITE)`。

**M3. 登录/改密接口无速率限制（暴力破解面）**
- 位置：`AuthController.login`（`auth/LoginRequest`）、`AuthController.changePassword`；`deploy/nginx-family-storage.conf` 无 `limit_req`
- 问题：`/api/auth/login` 公开可达且无任何限流/锁定；`changePassword` 会发生多次密码猜测+撞库。家庭系统密码仅要求 6 位，风险更高。
- 修复建议：nginx 对 `location /api/auth/`（尤其 login）加 `limit_req zone=login:10m rate=5r/m`；或后端引入简易登录失败计数/退避。成本低、收益明确。

**M4. 全局异常兜底把输入类错误一律映射 500，且不落库日志**
- 位置：`GlobalExceptionHandler.java:33-36`
- 问题：`@ExceptionHandler(Exception.class)` 会吞掉 `HttpMessageNotReadableException`（畸形 JSON）、`MissingServletRequestParameterException`、`MethodArgumentTypeMismatchException`、`DataIntegrityViolationException`（如 price 超 DECIMAL(10,2)、并发重名）等本应 400 的请求级错误，全部返回 500 `INTERNAL_ERROR`，与「错误码与 HTTP 状态一致」约定不符；且未记录异常日志，生产排障无痕。
- 修复建议：分别增加 400 类 handler（HttpMessageNotReadable / MethodArgumentNotValid 已有时补充 ServletRequestBinding 等），并在 catch-all 中 `log.error(..., e)`。

**M5. 上传仅校验扩展名+Content-Type，无魔数嗅探；静态直出无 nosniff**
- 位置：`FileStorageService.store:49-61`、`deploy/nginx-family-storage.conf:37-41`
- 问题：客户端可声明 `Content-Type: image/jpeg` + `.jpg` 扩展名上传任意字节内容（HTML/脚本 polyglot）。当前 `/uploads` 由 nginx 直出、无 `X-Content-Type-Options: nosniff`、无 CSP，配合浏览器内容嗅探存在存储型 XSS 的低概率路径；即使只作图片展示，也建议加魔数校验。
- 修复建议：① `store()` 增加魔数校验（JPEG/PNG/WebP 文件头）；② nginx `/uploads` 增加 `add_header X-Content-Type-Options nosniff;` 与 `Content-Disposition`（按需）；③ 后端 Test 补充伪造 image/jpeg 内容被拒的断言。

**M6. 后端无「最后一个启用 ADMIN」保护，误操作可致全家失去管理入口**
- 位置：`UserService.setStatus:61-66`、`UserService.update:49-59`
- 问题：前端 `UsersView.onToggleEnabled` 已拦截停用 ADMIN（`UsersView.vue:95-98`），但后端 `setStatus`/`update` 不校验：API 直接调用可停用唯一管理员、将唯一 ADMIN 降级为 MEMBER、或 ADMIN 自停用/自降级。恢复路径需要另一个 ADMIN，否则系统管理入口永久丢失。
- 修复建议：后端在 setStatus/update 前校验「若目标为启用态 ADMIN 且系统中启用 ADMIN≤1，则拒绝（400/403）」，可选禁止停用/降级自己。

**M7. 首用户注册 TOCTOU 竞态：并发两个首个注册者都可能成为 ADMIN**
- 位置：`AuthService.register:27-41`
- 问题：`userRepository.count() > 0` 判定与 `save` 不在同一原子临界区内，并发时两个请求都读到 count=0，各以 `Role.ADMIN` 建号。DB 只有 username 唯一约束，防不住两个不同用户名的并发注册。
- 修复建议：给 `register` 加事务并配合 DB 级互斥（如 `SELECT ... FOR UPDATE` 用户表 / 或引入 `system_bootstrap` 单行标志位 / Flyway V2 新增仅允许 count=0 时注册的存储判定）。实际影响低（家庭首次使用），但属原则问题。

### 2.3 LOW / 建议（可择期）

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| L1 | `CategoryController.java:28-51`、`NotificationController.java:26-44` | 直接返回 JPA 实体（Category/Notification），未走 DTO；当前字段无敏感信息但已偏离「DTO 与实体隔离」 | 改为与 Item/Location 一致返回响应 DTO |
| L2 | `UserService.create:39-46` | 「留空密码由后端随机生成」生成的 8 位密码**没有任何渠道告知创建人**，响应不含 raw，唯一救场是再走一次 reset-password | 创建响应一次性返回初始密码（密码仅返回一次），或去掉随机生成路径强制管理员填写 |
| L3 | `ItemRequest.photoUrl` + `ItemService.apply:192` | photoUrl 不校验来源：可填任意外链地址（引流、跟踪像素、踩 xss 边界） | 校验 `photoUrl` 必须以 `/uploads/` 开头且文件存在，否则 400 |
| L4 | `ItemRequest.purchasePrice`（`item/dto/ItemRequest.java:20`） | 无 `@Digits/DECIMAL(10,2)` 上限，超长数字 → DB 约束 → 500 | DTO 补 `@Digits(integer=8, fraction=2)` + 非负校验 |
| L5 | `ItemRepository.java:25` | 关键词 LIKE 未转义 `%`/`_`：搜索 `%` 返回全部 | `StringEscapeUtils` 或 replace `\`→`\\`、`%`→`\%`、`_`→`\_`（参数化已防注入，此为语义问题） |
| L6 | `HomeView.vue:12`、`ItemsView.vue:17-18` vs `SettingsService` | 前端临期窗口硬编码 7/30 天，而后端通知阈值可配（1-90）；设置项改了首页/搜索展示不会变，可能造成「设了 30 天却没在首页看到提醒」的困惑 | 前端从 `/api/settings` 读阈值，或明确定义「首页 7 天为固定展示窗口」并在文案说明 |
| L7 | `deploy/start.sh:26-42` | `set -u` 但无 `set -e`；JWT_SECRET 未做长度校验（<32 字节时 jjwt 启动抛错但语义不清晰） | 增加 `${#JWT_SECRET} -ge 32` 校验并明确报错文案 |
| L8 | `SecurityConfig.java:38` / nginx `/uploads` 直出 | 上传图片对**未认证访问者公开**（拿到 URL 即可看家庭照片）。与「前端 `<img>` 无 Authorization」的设计配套，内网可接受 | 若对外网部署：`/uploads` 改签 URL 或加基础认证；评审按当前设计放行（需产品确认） |
| L9 | `SecurityConfig.java:39` | `swagger-ui`、`v3/api-docs` 公开暴露 API 面 | 生产 profile 关闭 springdoc 或置于 ADMIN 权限 |
| L10 | `store/auth.ts:10`（localStorage JWT） | token 存 localStorage，可被 XSS 读取；本项目无 `v-html`、上传与渲染路径受控，风险已缓释 | 若收紧可改 httpOnly Cookie（需同步 CSRF 策略） |
| L11 | `CategoryService.create:31-37` | 重名预检查非原子，并发同名仍会撞唯一索引 → 500 | 补 `DataIntegrityViolationException` → 400 `CATEGORY_NAME_TAKEN` 映射 |
| L12 | `application.yml:15`（flyway `baseline-on-migrate: true`） | 全新空库正常执行 V1；若服务器已有 schema 但无 flyway 历史表会产生「V1 被基线跳过」的风险 | 部署文档明确「生产库须为空 schema」或改用 `baseline-version: 0` |
| L13 | `ItemEditView.vue:95-122` | 照片上传与物品保存分离：用户上传后取消表单/不保存，服务器产生孤儿文件；删除照片失败时 photoUrl 已清但远端文件残留 | 表单关闭/取消时对本次新建未保存的 `photoUrl` 触发 delete；改密等失败提示后回滚 UI |
| L14 | `StatisticsService.byCategory/byLocation`、`LocationService.get` | 每次统计/详情重建全量维度映射与树，`Pageable` 无上限保护（默认 2000/页）；家庭规模无碍 | 统计映射改 join/批量 fetch；全局 `spring.data.web.pageable.max-page-size` 设 100 |

---

## 三、做得好的点

- **契约一致性**：前后端与 spec §5 逐一对应（含 R13 `enabled` 字段、INITIAL 内部类型、CANCELLED 软删除语义）；api-tester 79/79 与 E2E 46 步均绿盘；D1/D2/D3 历史缺陷有回归测试（`FileStorageServiceTest` 12 例含穿越回归）。
- **权限分层清晰**：`SecurityConfig` 路由级（`/api/users/**`、`PUT /api/settings` → ADMIN）+ 组件内 `ensureAdmin` 双保险；通知/我的数据均按 `@AuthenticationPrincipal` 限定本人。
- **文件安全**：扩展名+Content-Type 白名单、5MB 上限、UUID 落盘、路径穿越防删除（`deleteRejectsTraversalPath` 等验证），起止一致。
- **AppSetting 保留字**：实体 `@Column(name = "\`key\`")` 与 V1 DDL 反引号一致，E02 已修复。
- **SQL 注入**：全部 JPQL 参数化（`@Param`、between、Pageable），无字符串拼接；LIKE 注入面不存在。
- **通知幂等**：`uk_notifications_user_reminder` 唯一索引 + `existsBy` 短路 + `DataIntegrityViolationException` 吞并，调度无重入问题。
- **事务**：`create`（物品+INITIAL 记录）、`delete`（物品+级联历史）、`adjustStock` 均在单事务内，失败不残留。
- **前端体质**：无 `v-html`、类型完备、上传 5MB/类型双重校验、loading/error/empty 三态齐全、轮询 `onUnmounted` 清理、搜索/筛选去抖封装成纯函数可测。
- **密钥管理**：start.sh 强制 `JWT_SECRET`/`DB_PASSWORD` 非空、`.env.example` 无真实值、`.gitignore` 覆盖 `.env`/`uploads`/`dist`。

---

## 四、评审结论

**APPROVED_WITH_NOTES（有条件通过）**

- **必修（上线前）**：H1 移除生产 JWT 兜底密钥（动态值与注释只留一处）。
- **强烈建议（上线前评估）**：M1 禁用 token 生效、M3 登录限流、M4 错误映射与日志、M6 最后管理员保护。
- **建议**：M2/M5/M7 与 L1-L14 按排期处理；均不阻塞当前交付。

修复建议已逐条给出上文落点；修改由作者执行，本评审只读不改码。

---

## 五、缺陷修复记录（2026-09-24）

执行人：backend Agent（Task 17 Step 4）；修复基线 HEAD `e03af94`；修复提交 `13bb126`（fix: address code-review findings）。
验证：`mvn test` 全量 88 用例（既有 58 + 新增 30）全绿（`cmd /c "set JAVA_HOME=D:\Java\jdk-17&& set DB_PASSWORD=iflytek&& D:\apache-maven-3.9.16\bin\mvn.cmd test"`，workdir `backend\`）。

| # | 状态 | 处理说明 | 回归测试 |
| --- | --- | --- | --- |
| H1 | ✅ 已修 | `application-prod.yml` 的 `app.jwt.secret` 去默认值改为 `${JWT_SECRET}`，缺失即启动失败（快速失败，与 DB_PASSWORD 一致）；`deploy/start.sh` 增加 `${#JWT_SECRET} -ge 32` 长度校验，弱密钥拒绝启动；dev `application.yml` 保留兜底但加注释强调仅供开发。 | 无（配置层，由 `StorageApplicationTests.contextLoads` + dev profile 覆盖编译/加载路径） |
| M1 | ✅ 已修 | `JwtAuthFilter` 解析 token 后使用 `UserRepository.existsByIdAndEnabledTrue(userId)`（一次主键查询/请求，索引命中，家庭规模开销可忽略；未引入缓存以换取停用即时生效）校验用户存在且启用；停用/不存在 → 401 `USER_DISABLED` 并截断请求，不再依赖 168h 过期兜底。 | `JwtAuthFilterTest.disabledUserTokenRejectedWith401UserDisabled`、`enabledUserTokenPassesThrough`、`invalidTokenContinuesAnonymous`、`noHeaderPassesThrough` |
| M2 | ✅ 已修 | `Item` 加 `@Version` 乐观锁（含 getter/setter），并发出入库后写者收到 `ObjectOptimisticLockingFailureException`；`GlobalExceptionHandler` 将 `ObjectOptimisticLockingFailureException`/`jakarta.persistence.OptimisticLockException` 映射 409 `STOCK_CONFLICT`（带重试提示）。**偏差说明**：任务括号标注"@Version 无 DDL"在 `ddl-auto=none`+Flyway 生产 schema 下不成立——@Version 必须有持久化列，否则 SELECT/UPDATE 直接 SQL 报错；故新增纯增量迁移 `V2__add_items_version.sql`（加 `version BIGINT NOT NULL DEFAULT 0` 列，回填 0、无契约字段变更），属基础设施列添加而非「动数据库结构方案」。 | `StockChangeServiceTest.optimisticLockConflictPropagatesFor409Mapping`、`GlobalExceptionHandlerTest.optimisticLockConflictMapsTo409Conflict` |
| M3 | ✅ 已修 | 新增 `LoginRateLimiter`（纯 JDK `ConcurrentHashMap`，按「用户名\|IP」维度；连续 5 次失败锁定 5 分钟，成功即清零，429 `TOO_MANY_ATTEMPTS`）；接入 `AuthController.login/register`（IP 优先取 `X-Forwarded-For` 首地址）。`deploy/nginx-family-storage.conf` 顶部给 `limit_req_zone $binary_remote_addr zone=auth_login:10m rate=10r/s`（http 上下文）启用样例，`location /api/auth/` 内附 `limit_req zone=auth_login burst=10 nodelay;` 注释启用位。 | `LoginRateLimiterTest.*`（5 例）、`AuthServiceTest.loginLockedAfterFiveConsecutiveFailures`、`successfulLoginClearsFailureCounter`、`registerUsernameTakenFailuresAlsoLockOut` |
| M4 | ✅ 已修 | `GlobalExceptionHandler` 新增 `HttpMessageNotReadableException`/`MethodArgumentTypeMismatchException`/`MissingServletRequestParameterException` → 400 `BAD_REQUEST`；`DataIntegrityViolationException` → 409 `DATA_CONFLICT`（业务码优先原则：业务先抛 `ApiException` 则不达此处）；catch-all 500 前 `log.error("Unhandled exception ...", e)` 留痕。 | `GlobalExceptionHandlerTest.malformedJsonMapsTo400BadRequest`、`dataIntegrityViolationMapsTo409Conflict` |
| M5 | ✅ 已修 | `FileStorageService.store` 在扩展名+Content-Type 白名单之外增加文件头魔数校验（JPEG `FF D8 FF`；PNG `89 50 4E 47 0D 0A 1A 0A`；WebP `RIFF....WEBP`），不一致 → 400 `INVALID_FILE_TYPE`；`deploy/nginx-family-storage.conf` 的 `/uploads` 增加 `X-Content-Type-Options: nosniff` + `Content-Security-Policy: default-src 'none'`（图片直出场景防 MIME 嗅探 + 直接导航纵深防御）。既有"用假字节存图"的用例已同步改为真实魔数头。 | `FileStorageServiceTest.htmlDisguisedAsJpegRejectedByMagic`、`htmlDisguisedAsPngRejectedByMagic`、`blocksMismatchedMagicBetweenFormats`、`validJpegAccepted`、`validPngAccepted`、`validWebpAccepted` |
| M6 | ✅ 已修 | `UserService.setStatus`（停用）与 `update`（ADMIN→非 ADMIN 降级）前置校验：`countByEnabledTrueAndRole(ADMIN) <= 1` 时拒绝 → 400 `LAST_ADMIN`；非启用态/非 ADMIN 不触发查询。 | `UserServiceTest.disablingLastAdminIsRejected`、`disablingAdminAllowedWhenAnotherEnabledAdminExists`、`demotingLastAdminIsRejected`、`demotingAdminAllowedWhenAnotherEnabledAdminExists`、`disablingMemberIsAlwaysAllowed`、`disablingAlreadyDisabledAdminIsAllowed` |
| M7 | ✅ 已修 | `AuthService.register` 首用户判定放入 JVM `synchronized(bootstrapLock)` + `TransactionTemplate`（事务与提交均在锁内完成，闭合"锁内 count、锁外提交"的残留窗口）：并发首个注册者被串行化，后到者在事务内 `count()` 复查看到已提交行 → `REGISTRATION_CLOSED`。单实例部署下锁+事务即 DB 级互斥；多实例需换 Redis/DB 唯一约束（已在类注释说明）。 | `AuthServiceTest.concurrentFirstRegistrationsCreateSingleAdmin` |
| L4（顺手项） | ✅ 已修 | `ItemRequest.purchasePrice` 增加 `@DecimalMin("0.00")`/`@DecimalMax("99999999.99")`/`@Digits(integer=8, fraction=2)`；`GlobalExceptionHandler` 对该字段校验失败映射 400 `INVALID_PRICE`（其余字段仍 `VALIDATION_ERROR`）。 | `GlobalExceptionHandlerTest.priceValidationMapsToInvalidPrice` |
| L1/L2/L3/L5-L14 | ⚠️ 有意不修 | 评估结论：L1/L3/L5/L9/L10/L12 为设计取舍或需产品确认（DTO 隔离、photoUrl 来源、LIKE 转义、swagger 暴露、localStorage、flyway baseline），家庭内网场景当前不构成交付阻塞；L2（初始密码回显）、L6（前端阈值硬编码）、L7 长度校验（已在 H1 顺带修复）、L8（uploads 公开，需产品确认）、L11（并发重名撞唯一索引，现由 M4 的 `DATA_CONFLICT` 兜底不再 500）、L13（前端孤儿文件）、L14（统计重建，家庭规模无碍）建议下一迭代排期处理。 | — |

补充说明：本次未引入新重型依赖（限流用 JDK 集合、乐观锁用 JPA 内置）；`docs/reports/code-review.md` 之外，简版修复记录见 `.superpowers/sdd/2026-09-24-family-storage-system/task-17-fix-report.md`。