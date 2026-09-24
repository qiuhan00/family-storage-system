# 前端集成回归 E2E 测试报告（Task 15 · 家庭收纳管理系统）

- 被测应用：家庭收纳物品管理系统（Vue3 + Vite 前端 / Spring Boot 后端 + MySQL）
- 被测 URL：`http://localhost:5173/`（前端 dev），后端 `http://localhost:8080`（`/uploads` 静态代理）
- 浏览器 / 视口：Chrome（chromium channel chrome）/ 1280×720 / headless
- 执行时间：2026-09-24 23:00（最终证据运行 · 全新隔离库）
- 测试环境：worktree `family-storage-system`，隔离库 `family_storage_e2e`（utf8mb4_unicode_ci，Flyway 重建）
- 测试脚本：`docs/e2e/scripts/family-storage.spec.js` + `playwright.config.js`（Playwright 1.63）
- 场景清单：`docs/e2e/scenarios.csv`（94 步）
- 证据目录：`docs/e2e/evidence-2026-09-24-2300/`（46 张步骤截图 + 视频/trace/HTML 报告）

## 结论

**PASS（1/1 全流程通过，26.5s）**。未登录守卫、注册（新库）、首页统计与 Tabbar、物品「增改查 + 照片上传 + 筛选 + 出入库 + 盘点错误分支」、购物「新增/勾选/已购/待购/全部 Tab」、通知角标、我的「改密/退出/错误密码分支/新密码登录」、成员管理（管理员保护 + 启停持久化）、临期阈值 7→30 生效、注册关闭分支、最终网络/JS 异常清扫均验证通过。

流程中发现的 4 个前端缺陷均已在本次会话修复并复测通过（见缺陷清单）。

## 流程步骤表（PASS）

| # | 场景 | 证据 |
|---|------|------|
| 1 | 未登录访问 `/` 重定向 /login | step-01-login-redirect.png |
| 2 | 注册首账号（e2eadmin / 昵称测试管理员） | step-02-register-filled.png |
| 3 | 注册成功自动登录进首页 | step-03-home-after-register.png |
| 4 | 首页 5 张统计卡 + Tabbar 四入口（临期 1 项/待购 0） | step-04-home-stats-tabbar.png |
| 5 | 新增物品：分类（临时创建「饮料」「日化」） | step-05-item-form-category.png |
| 6 | 位置二级联动（客厅→书架） | step-06-item-form-location-cascade.png |
| 7 | 日期选择（购买今天 / 到期 +3 天） | step-07-item-form-dates.png |
| 8 | 6MB 大图被拒（「图片不能超过 5MB」） | step-08-upload-oversize-rejected.png |
| 9 | 合法小图上传成功（blob 缩略图回显） | step-09-upload-ok-thumbnail.png |
| 10 | 物品列表包含新物品 | step-10-items-list-first-item.png |
| 11 | 详情页照片经 `/uploads` 代理可加载（naturalWidth>0） | step-11-detail-photo.png |
| 12 | 详情信息 / 到期标签正确 | step-12-detail-info-expiry.png |
| 13 | 编辑表单回填（数量 5 盒 + E2E 备注修改） | step-13-edit-filled.png |
| 14 | 保存后详情页刷新（数量 5 盒 / 备注） | step-14-detail-after-edit.png |
| 15 | 搜索「牛奶」命中 | step-15-search-hit.png |
| 16 | 搜索无结果空态 | step-16-search-empty.png |
| 17 | 分类筛选（仅饮料） | step-17-filter-category.png |
| 18 | 临期筛选（仅牛奶）与重置 | step-18-filter-expiring.png |
| 19 | 入库 +2（弹窗备注） | step-19-stock-in-dialog.png |
| 20 | 入库后数量 7盒 / 历史入库记录 | step-20-history-after-in.png |
| 21 | 盘点 -99999 弹窗 | step-21-stock-oversell-dialog.png |
| 22 | 超存量被拒（「库存不足，出库数量超出当前存量」） | step-22-stock-oversell-error.png |
| 23 | 出库 -1 后 6盒 + 历史 | step-23-history-after-out.png |
| 24 | 购物新增表单（缺什么买什么） | step-24-shopping-add-form.png |
| 25 | 新增「洗洁精 1 瓶」→ 待购 | step-25-shopping-pending.png |
| 26 | 勾选标记已购 → PATCH 200 → 已购 | step-26-shopping-bought.png |
| 27 | 「已购」Tab 过滤 | step-27-shopping-tab-bought.png |
| 28 | 「待购」Tab 空态 | step-28-shopping-tab-pending-empty.png |
| 29 | 通知种子（模拟调度产物）→ 首页角标 1 | step-29-home-badge.png |
| 30 | 我的：资料卡（昵称/管理员） | step-30-profile-card.png |
| 31 | 修改密码表单 | step-31-change-password-form.png |
| 32 | 密码已修改（旧→E2eNew123!） | step-32-change-password-ok.png |
| 33 | 退出登录回 /login | step-33-logout.png |
| 34 | 错误密码登录被拒（用户名或密码错误） | step-34-login-fail.png |
| 35 | 新密码登录成功 | step-35-users-list.png |
| 36 | /users：管理员不可停用 | step-36-users-admin-switch-blocked.png |
| 37 | 新增成员表单（密码留空随机） | step-37-users-create-form.png |
| 38 | 成员创建成功（member1 启用中） | step-38-users-member-created.png |
| 39 | 停用 member1 → 已停用 | step-39-users-member-disabled.png |
| 40 | reload 后回显已停用（真实状态） | step-40-users-member-disabled-reload.png |
| 41 | 再启用 → reload 保持启用 | step-41-users-member-enabled-reload.png |
| 42 | /settings：阈值 7 → 30 | step-42-settings-filled.png |
| 43 | 保存成功 + 文案 30 天 + PUT/GET 持久化 | step-43-settings-saved.png |
| 44 | 阈值生效：首页临期提醒 = 2项 | step-44-home-threshold-effective.png |
| 45 | 注册关闭分支（系统非空 → 提示联系管理员） | step-45-register-closed.png |
| 46 | 最终清扫：无 pageerror / 无意外 4xx+ / 无 failed 请求 | step-46-final-clean-console.png |

## 缺陷清单（本会话发现并修复，均已复测）

| 编号 | 位置 | 期望 | 实际 | 证据 | 修复 |
|------|------|------|------|------|------|
| D0 | `frontend/src/views/ItemEditView.vue`（beforeRead） | 合法图片可上传 | `beforeRead` 对合法文件返回 `undefined`，被 Vant 视为「中止上传」→ 照片上传完全失效 | probe.spec.js（探针）+ step-08/09 | 合法文件 `return true` |
| D1 | `frontend/vite.config.ts` | 详情页照片可显示 | 前端无 `/uploads` 代理，dev 下图片 404 | step-11 | 增加 `'/uploads': 'http://localhost:8080'` 代理 |
| D2 | `frontend/src/views/ShoppingView.vue`（van-checkbox） | 勾选标记已购 | 单向 `:model-value` + `@change`：Vant 仅在 modelValue 实际变化（watch）后才 emit change，单向绑定时永不变 → 勾选点击无任何效果、不发出 PATCH | step-26（修复前无请求） | 改用 `@update:model-value` 触发 `onToggle`（与 UsersView Switch 同款模式） |
| D3 | `frontend/src/views/LayoutView.vue`（Tabbar 首页） | 任意页面点「首页」回 `/` | `to="/"` 的 item 在 `$route.matched` 中恒匹配父级 Layout 的 `path:'/'` → 首页被视为「已激活」，Vant `if(!active)` 跳过导航 → 首页 Tab 在物品/购物/我的均不可点（DOM click 亦无效） | probe5.spec.js：/shopping 点首页 URL 不变 | 改为 `:to="{ name: 'home' }"` 按路由名匹配，仅 `/` 时激活 |

> 复测：修复后在同一隔离库重跑全流程，46 步全部 PASS；`PATCH /api/shopping/*/status` 正常发出、Tabbar 首页导航至 `/` 且 `home-page` 可见。

## 交互 / 视觉问题

- 本次运行未见布局错乱、响应式问题或交互无反馈。
- 说明：控制台残留的 404（favicon）、400（故意触发盘点超存量）、401（错误密码分支）、403（注册关闭分支）均为场景内预期行为，已在最终断言中豁免并单独列出（见 spec 末尾 CONSOLE_ERRORS(INFO)）。

## 复现与运行方式（约束：不改后端代码、不落盘敏感信息）

1. 环境变量 `DB_PASSWORD`（User 级）提供 MySQL root 密码；不在仓库/脚本中硬编码。
2. 隔离库重置脚本 `reset-e2e.ps1`（temp 目录，纯 ASCII 内容）：
   - 杀 8080 监听进程 → `DROP/CREATE family_storage_e2e` → 以 `D:\apache-maven-3.9.16\bin\mvn.cmd` + `JAVA_HOME=D:\Java\jdk-17` 启动后端（`JAVA_TOOL_OPTIONS` 注入 `spring.datasource.url` 与 `spring.flyway.default-schema`）→ 等待 "backend ready"。
3. 每轮运行前必须重置库（数据残留会破坏「暂无临期物品」、临期统计=1 等断言）。
4. 运行：`$env:DB_PASSWORD=...; $env:EVIDENCE_DIR=<证据目录>; npx playwright test family-storage.spec.js`。

## 产物

- 证据截图：`docs/e2e/evidence-2026-09-24-2300/step-*.png`（46 张）
- 视频 / trace / HTML 报告：`docs/e2e/evidence-2026-09-24-2300/{video.webm, trace.zip, html-report}`
- 脚本与配置：`docs/e2e/scripts/`