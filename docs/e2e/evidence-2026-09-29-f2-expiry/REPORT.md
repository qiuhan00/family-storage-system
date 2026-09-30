# F2 临期阈值统一 · 移动端 E2E 验收报告

- 被测：家庭收纳物品管理系统（Vue3 + Vant 前端 `http://localhost:5173` → proxy `/api`、`/uploads` → Spring Boot `http://localhost:8080`，主库 `family_storage`）
- 被测缺陷：**F2 —— 临期口径未统一到 `app_settings` 的 `expiry.remind.days`**（应由 `GET /api/settings → expiry_remind_days` 单点驱动首页标题 / 临期列表 / 筛选文案 / 角标 / `?expiring=1` 搜索窗口）
- 浏览器与视口：Google Chrome（channel=`chrome`，headless），**390×844**（deviceScaleFactor 2，竖屏移动视口）
- 执行时间：2026-09-29 17:41–17:45（最终用例 `1 passed (14.9s)`，其中用例 12.9s）
- 场景文件：`docs/e2e/scenarios-f2-expiry.csv`（33 步）
- 测试脚本：`docs/e2e/scripts/f2-expiry-threshold.spec.js` + `docs/e2e/scripts/playwright.f2.config.js`（副本置于 `%LOCALAPPDATA%\Temp\opencode\e2e-pw\` 执行）
- **结论：PASS（33/33 步通过，0 个 F2 相关缺陷）**

## 1. 执行结果

| 项目 | 结果 |
| --- | --- |
| 场景总步数 | 33 |
| PASS | 33 |
| FAIL | 0 |
| Playwright 断言 | `1 passed`（0 failed） |
| JS 未捕获异常（pageerror） | 0 |
| 非预期 console error | **0**（白名单 2 条，见 §4） |
| 失败网络请求 | 0 |
| 运行命令 | `$env:EVIDENCE_DIR='D:\xcode\wt\docs\e2e\evidence-2026-09-29-f2-expiry'; npx playwright test f2-expiry-threshold.spec.js --config=playwright.f2.config.js` |

### 按场景分组

| 组 | 场景 | 步数 | 结果 | 关键证据 |
| --- | --- | --- | --- | --- |
| 前置 | 未登录重定向 / 登录 | 1–6 | PASS | step-01、step-03 |
| 错误分支 | 错误密码 → 「用户名或密码错误」且停留 `/login`（POST 401） | 4 | PASS | step-02、console-network-log |
| A | 阈值=7 全链路：首页标题「7 天内」、统计卡=列表条数、`expiring?days=7`、筛选文案「仅看 7 天内临期」、角标（近=红 / 远=普通非橙 / 已过期=红 / 无到期=无）、详情页红/普通、`?expiring=1` 仅 1 条 | 7–22 | PASS | step-04 ~ step-13 |
| B | 阈值改 30 保存后：首页标题「30 天内」、计数一致且含 +15 天物品、筛选文案「仅看 30 天内临期」、远期角标变红「临期」、`?expiring=1` = 2 条且与 API `totalElements` 一致 | 23–29 | PASS | step-14 ~ step-21 |
| C | 恢复阈值 7：保存成功、API=7、首页标题与筛选文案复原 | 30–32 | PASS | step-22 ~ step-24 |
| D | 全程 console / pageerror 质量门禁 | 33 | PASS | step-25、console-network-log.txt |

## 2. 截图证据清单（25 张，`deviceScaleFactor=2`）

| 文件 | 步骤 | 内容 |
| --- | --- | --- |
| `f2-step-01-a0-login-page.png` | 1 | 登录页（登录前） |
| `f2-step-02-a1-login-error-branch.png` | 4 | **错误分支**：错误密码登录后提示「用户名或密码错误」 |
| `f2-step-03-a2-home-after-login.png` | 6 | 登录成功进首页 |
| `f2-step-04-a3-home-title7-counts.png` | 7–8 | 「临期提醒（7 天内）」+ 统计卡 1 项 + 列表 1 行 |
| `f2-step-05-a4-filter-text-7.png` | 11 | 筛选面板「仅看 7 天内临期」 |
| `f2-step-06-a5-badge-near-red.png` | 13 | 近期卡片：红色「临期 2026-10-02」 |
| `f2-step-07-a6-badge-far-normal.png` | 14 | 远期卡片：普通「到期 2026-10-14」（**非橙非红**） |
| `f2-step-08-a7-badge-expired-red.png` | 15 | 牛奶（已过期 2026-09-28）：红色「临期」 |
| `f2-step-09-a8-badge-none.png` | 16 | 无到期物品：无角标 |
| `f2-step-10-a9-items-list-7.png` | 13–16 | 阈值 7 下整页列表 |
| `f2-step-11-a10-detail-near-red.png` | 17 | 详情页：红色「临期」 |
| `f2-step-12-a11-detail-far-normal.png` | 19 | 详情页：普通「到期」（旧橙色档已删） |
| `f2-step-13-a12-expiring-search-7.png` | 21 | `?expiring=1`（阈值 7）→ 仅 1 条 |
| `f2-step-14-b1-settings-7.png` | 23 | 设置页当前值 7 |
| `f2-step-15-b2-settings-30-filled.png` | 24 | 输入 30，输入框与提示同步 |
| `f2-step-16-b3-settings-saved-30.png` | 25 | 保存成功 toast，API=30 |
| `f2-step-17-b4-home-title30-counts.png` | 26 | 「临期提醒（30 天内）」+ 统计 2 项 = 列表 2 行 |
| `f2-step-18-b5-filter-text-30.png` | 27 | 筛选面板「仅看 30 天内临期」 |
| `f2-step-19-b6-badge-far-red-30.png` | 28 | 阈值 30：远期变红色「临期 2026-10-14」 |
| `f2-step-20-b7-items-list-30.png` | 28 | 阈值 30 整页列表 |
| `f2-step-21-b8-expiring-search-30.png` | 29 | `?expiring=1`（阈值 30）→ 2 条 |
| `f2-step-22-c1-settings-restored-7.png` | 30 | 恢复保存 7 |
| `f2-step-23-c2-home-title7-restored.png` | 31 | 标题恢复「7 天内」 |
| `f2-step-24-c3-filter-text-7-restored.png` | 32 | 筛选文案恢复「7 天内」 |
| `f2-step-25-d1-final-console-clean.png` | 33 | 终态：无 console 错误弹层 |

其他证据：`recording.webm`（1.3 MB 全程录像）、`trace.zip`（8.3 MB）、`html-report/`、`test-results/`、`run.log`、`console-network-log.txt`。

## 3. 角标颜色的客观核验（像素扫描，不依赖肉眼读图）

`scripts/f2-pixel-check.ps1`（每 2px 采样，判定 `R>170 && G<90 && B<90`=红 `#dc2626`；`R∈[190,235] && G∈[95,150] && B≤90`=橙 `#d97706`）：

| 截图 | 场景 | redPx | orangePx | 判定 |
| --- | --- | --- | --- | --- |
| step-06 近期角标 @阈值7 | A | **294** | 30 | 红「临期」✅ |
| step-07 远期角标 @阈值7 | A | **0** | 11 | 非红非橙 → 普通「到期」✅ |
| step-08 已过期角标 | A | **308** | 12 | 红「临期」✅ |
| step-09 无到期角标 | A | 0 | 8 | 无红 ✅ |
| step-11 详情近期 @阈值7 | A | **349** | 146 | 红 ✅ |
| step-12 详情远期 @阈值7 | A | **0** | 149 | 与 step-11 同橙色基线（146/149，来自页面其他元素）→ 角标非橙 ✅ |
| step-19 远期角标 @阈值30 | B | **291** | 10 | 红「临期」✅ |
| step-10 整页远期卡区域 @阈值7 | A | **0** | 9 | 非红非橙 ✅ |
| step-20 整页远期卡区域 @阈值30 | B | **291** | 8 | 红 ✅ |

> 结论：旧版「8–30 天区间显示橙色临期」的橙色档确已删除；同一物品在阈值 7/30 下角标由「到期」↔「临期（红）」正确切换。

## 4. 控制台与网络（`console-network-log.txt`）

- `page errors (uncaught)`：**空**
- `unexpected console errors (0)`
- 白名单 2 条（预期内，非 F2 缺陷）：
  1. `404 GET /favicon.ico` → **OBS-F2-01**
  2. `401 POST /api/auth/login` → 错误分支步骤故意触发
- `/api/items/expiring` 请求序列：`days=7 → days=30 → days=7`（跟随阈值切换）✅
- `/api/items?expiring=1`：2 次，条数分别与 `totalElements` 对齐（1 / 2）✅
- 无 failed requests

## 5. 发现项

| 编号 | 级别 | 描述 | 复现 / 证据 | 建议 |
| --- | --- | --- | --- | --- |
| **OBS-F2-01** | 轻微（P3，非阻塞） | 首页请求 `http://localhost:5173/favicon.ico` 返回 404，控制台出现一条资源加载错误噪音（非 JS 异常，不影响任何功能） | 任一页面强刷即可复现；`console-network-log.txt` | `frontend/index.html` 加 `<link rel="icon" href="/favicon.ico">` 并在 `frontend/public/` 放置图标文件 |
| **OBS-F2-02** | 口径需产品确认（非本次验收缺陷） | 角标规则按 F2「含已过期」显示红「临期」（如牛奶 2026-09-28），但后端统计卡与临期列表窗口为 `[today, today+N]`，**不含已过期**，故该物品不出现在「临期提醒」里 | step-08（列表有红临期）vs step-04（统计卡 1 项 = 列表 1 行，不含牛奶） | 与 architect/frontend 确认产品口径：已过期是否应计入提醒数；若需计入，改动在后端窗口计算，属新需求 |

未发现布局错乱、响应式问题或交互无反馈；筛选面板打开时背景遮罩会降低卡片颜色对比度（设计如此，已用像素扫描客观核验，见 §3）。

## 6. 过程记录（测试脚本调试，非应用缺陷）

| 尝试 | 结果 | 原因与处理 |
| --- | --- | --- |
| attempt1 | FAIL | 模板渲染的角标文本含前导空格 → 断言改为 `/^\s*临期 \d{4}-\d{2}-\d{2}\s*$/`，`not.toMatch(/^临期/)` → `not.toContain('临期')` |
| attempt2 | FAIL | 功能全绿，但 favicon 404 触发 console 门禁 → 改为记录 `{text,url}` 并白名单 401 与 `favicon.ico` |
| attempt3 | PASS | 截图未滚动到「仅看 N 天内临期」行 → 补滚动后重跑 |
| attempt4 | **PASS** | 本报告所依据的最终运行 |

`attempt1-FAIL/`、`attempt2-console-gate/`（已剔除其中 video/trace/html-report 以省空间）保留在证据目录可追溯。

## 7. 现场恢复

- 阈值：`GET /api/settings → expiry_remind_days = 7`（保存后 API + UI 双确认，场景 C）
- 测试种子：`F2E临期3天 / F2E远期15天 / F2E无到期` 已由 `f2-cleanup.mjs` 删除（HTTP 204），物品总数回到 4，`leftoverF2E: []`
- 复核（清理后）：`threshold=7, totalItems=4, expiring7=0`（剩余 4 条无 7 天窗口内物品，符合预期）
- 未使用 Playwright MCP，无 MCP profile 残留；`%LOCALAPPDATA%\ms-playwright`（浏览器二进制）保留未删
