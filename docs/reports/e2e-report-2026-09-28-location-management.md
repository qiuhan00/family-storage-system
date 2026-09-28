# 「位置管理」页面 E2E 测试报告（2026-09-28 · ui-tester）

- 被测 URL：`http://localhost:5173/`（Vite dev）/ 后端 `http://localhost:8080`（`/uploads` 静态代理）
- 被测页面：`/locations`（位置管理，新增 `frontend/src/views/LocationsView.vue`）+ 入口 `/profile`
- 浏览器 / 视口：Chrome（chromium channel `chrome`）/ **375×667（移动优先）** / headless
- 执行时间：2026-09-28 15:49 – 16:05（新场景 3 轮 + 回归 2 轮 + 16:30 补充时序探针）
- 测试环境：worktree `D:\xcode\wt\.worktrees\family-storage-system`，隔离库 `family_storage_e2e`
- 测试脚本：`docs/e2e/scripts/family-storage-locations.spec.js`（Playwright 1.63）
- 场景清单：`docs/e2e/scenarios-location-management.csv`（E-LM-01 .. E-LM-10，35 步）
- 证据目录：
  - 主证据 `docs/e2e/evidence-2026-09-28-1557-loc-management/`（28 张 `lstep-*.png` + `recording.webm` + `trace.zip` + `run3-playwright.txt`，注意日志以 `.txt` 入库，`.gitignore` 忽略 `*.log`）
  - 失败轮存档 `evidence-2026-09-28-1549-loc-attempt1-FAIL/`、`evidence-2026-09-28-1555-loc-attempt2-FAIL/`（断言口径修正过程，非应用缺陷）
  - 回归 `evidence-2026-09-28-1558-regress/`、`evidence-2026-09-28-1559-regress-defenses/`

## 结论

**PASS（新场景 16/16 标记通过，主流程 1 passed / 27.8s；既有全量回归 1 passed / 35.5s；防御分支 1 passed / 32.5s）**

- 位置管理全链路（注册→入口→空态→表单字段→必填校验→建根→建子+缩进→编辑→照片 4 分支→删除保护 4 分支→控制台/网络清扫）全部按场景 CSV 期望通过。
- 未捕获任何 `pageerror`、5xx、失败请求；4xx 仅为预期分支（注册关闭 403、删除保护 400×2，均已按 benign 规则放行）。
- 发现 **1 个低严重级视觉缺陷（DEF-LOC-01，保存 toast 与弹层标题过渡态重叠）**，不影响功能与数据；**已于同日修复并复测通过（见下「DEF-LOC-01」节的修复与复测记录）**。
- 测试结束后已清理：临时探针位置已删除、本次上传的 2 个照片文件 `DELETE /api/files/*` → 204。

## 流程步骤表（E-LM-01 .. E-LM-10 · 16/16 PASS）

| 标记 | 场景 / 断言要点 | 结果 | 证据（`evidence-2026-09-28-1557-loc-management/`） |
|---|---|---|---|
| E-LM-01 | 首账号注册（空库）→ 自动登录 ADMIN；复跑时注册关闭 → 回退登录 | PASS | `lstep-01..03`（本轮为复跑回退路径；**首跑注册成功证据**见 `evidence-2026-09-28-1549-loc-attempt1-FAIL/lstep-02-lm01-home-after-register.png`） |
| E-LM-02 | 我的页 `位置管理` cell（title/label 文案）→ `/locations` → 返回键回 `/profile` | PASS | `lstep-04..06` |
| E-LM-03 | 空态「共 0 个位置 / 暂无位置，点击右上角新增」+ 新增弹层字段（名称必填、父级=根节点、`0/500` 字数统计、位置照片） | PASS | `lstep-07..08` |
| E-LM-04 | 名称留空保存 → 提示「请输入位置名称」且 **不发** `POST /api/locations`，弹层不关闭 | PASS | `lstep-09`（+标注副本 `obs-LM04-inline-error-labeled.png`） |
| E-LM-05 | 新增根位置「厨房」+ 说明摘要回显 → toast「已保存」、弹层关闭、`共 1 个位置` | PASS | `lstep-10..11`（+ `obs-LM05-toast-after-close-labeled.png`） |
| E-LM-06 | 父级选择弹层改为「厨房」→ 新增「吊柜」；`paddingLeft` 厨房=12px / 吊柜=28px（depth 缩进） | PASS | `lstep-12..13`（+ `obs-DEF-LOC-01-*.png`） |
| E-LM-07 | 编辑「厨房」说明 → 保存后列表摘要更新为「一层台面放锅具」 | PASS | `lstep-14..15` |
| E-LM-08a | 6MB 图 → toast「图片不能超过 5MB」，**0 上传请求** | PASS | `lstep-16` |
| E-LM-08b | `note.txt` → toast「仅支持 jpg/jpeg/png/webp 图片」，**0 上传请求** | PASS | `lstep-17` |
| E-LM-08c | `tiny.png` 上传成功（预览 `blob:`）→ 保存 → 列表缩略 `src=/uploads/` 可加载、**44×44px** | PASS | `lstep-18..19` |
| E-LM-08d | 再次编辑 → 弹层回显服务器 `/uploads/` 缩略图；取消关闭 | PASS | `lstep-20` |
| E-LM-08e | `tiny.jpg` 上传 → 列表缩略图 | PASS | `lstep-21..22` |
| E-LM-09a | 删除有子级的「厨房」→ 确认弹窗 → toast「该位置下仍有子位置，请先删除子位置」（后端 400 `LOCATION_HAS_CHILDREN`），列表仍 2 行 | PASS | `lstep-23..24` |
| E-LM-09b | 删除有物品的「吊柜」→ toast「该位置下仍有物品，请先迁移或删除物品」（400 `LOCATION_IN_USE`），列表仍 2 行 | PASS | `lstep-25` |
| E-LM-09c | API 移除物品 → 删除「吊柜」→ toast「已删除」→ `共 1 个位置` | PASS | `lstep-26` |
| E-LM-09d | 删除「厨房」→ toast「已删除」→ 回到空态 `共 0 个位置` | PASS | `lstep-27` |
| E-LM-10 | 无 `pageerror` / 无未预期 4xx / 无 5xx / 无 failed 请求 | PASS | `lstep-28` + `run3-playwright.txt` |

### 控制台 / 网络（E-LM-10，`run3-playwright.txt`）

- `pageerror`：无；`server5xx`：无；`requestfailed`：无（favicon 除外）。
- 4xx 全部为预期分支：`403 POST /api/auth/register`（复跑注册关闭）、`400 DELETE /api/locations/1`、`400 DELETE /api/locations/2`（删除保护）。
- console error 仅对应上述 404(favicon)/403/400 的资源加载日志。
- 写请求足迹：`POST /api/locations` ×2、`PUT` ×3、`DELETE` ×4（保护 2 次 + 成功 2 次），与场景一致。

## 缺陷清单

### DEF-LOC-01 · 保存成功后 toast 与弹层标题过渡态重叠（严重级：**低** · 视觉）

- **复现**：登录 → 我的 → 位置管理 → 「新增位置」→ 填名称 → 点「保存」，观察保存成功瞬间（约点击后 450–550ms）。
- **期望**：toast 提示与弹层标题各自独立可读，互不遮挡。
- **实际**：toast（文案「已保存」）出现在屏幕中央，恰好覆盖底部弹层标题「新增位置」，两者文字叠加出现**叠字/糊字**；弹层随即在 ~800ms 内正常关闭，功能与数据正确。
- **证据**：
  - `evidence-2026-09-28-1557-loc-management/lstep-13-lm06-child-created-indented.png`（原图）
  - `obs-DEF-LOC-01-toast-title-overlap-labeled.png`（带文件名标签副本）、`obs-DEF-LOC-01-zoom-title.png`（6× 放大）
- **程序化佐证**（16:30 时序探针 `probe-popup-timeline`，保存后每 100ms 采样 `.van-popup` / `.van-toast` 矩形）：
  - `ms=497`：popup `top=289`（关闭动画中）、toast=`已保存`、**`overlap=true`**（toast 矩形与 `.form-panel__title` 矩形相交）
  - `ms=606` 起：popup 下移至 459/666，`ms=825` `display:none`（弹层关闭成功）
  - 编辑路径同现象（`ms=500..721` 关闭动画，toast 常驻 ~2s）
- **影响面**：仅视觉过渡态（约 300ms 窗口），无功能/数据影响；建议前端将 toast 位置上移（避开弹层标题区）或关闭弹层后再弹 toast。
- **回归风险**：低；不阻塞验收。
- **状态：✅ 已修复（2026-09-28，frontend 执行 code-review 建议）**

#### DEF-LOC-01 修复与复测（Y2 证据落盘）

- **修复方案**（`frontend/src/views/LocationsView.vue`）：
  1. 保存成功不再立即 `showToast`，改为写入 `pendingToast` 并先 `showForm = false` 关闭弹层；
  2. 在 `van-popup` 的 **`@closed`（关闭动画完全结束事件）** 回调 `onPopupClosed()` 中再弹出 toast；
  3. 首版曾用 `@after-leave`——Vant 4.9.15 Popup 不提供该事件（只有 `open/close/opened/closed/…`），toast 永不弹出，已废弃改用 `@closed`。
- **复测方法**：受控 Playwright 时序采样（登录 → `/locations` → 新增/编辑各执行一次保存，点击后每 100ms 采样 23 次，用矩形相交判定 toast 与 `.form-panel__title` 是否重叠）。
- **复测脚本**：`docs/e2e/scripts/verify-def-loc-01-fix.js`（自写输出，规避 `*.log` 忽略）
- **复测证据**：`evidence-2026-09-28-1557-loc-management/fix-def-loc-01-verify.txt`
- **复测结果**：

| 路径 | 采样数 | toast 出现（首采样 ms） | `OVERLAP_TRUE` |
|---|---|---|---|
| CREATE | 23 | 19（ms=400，关闭动画后） | **0** |
| EDIT | 23 | 23（ms=0 起含上条 toast 尾迹） | **0** |
| 合计 | **46** | — | **0 → PASS** |

- **回归**：`vue-tsc + npm run build` ✅、`vitest 20/20` ✅（修复前后各跑一轮）。

## 交互 / 视觉观察（非缺陷，供 frontend / architect 决策）

1. **必填校验采用内联错误而非 toast**：E-LM-04 中「请输入位置名称」以 `.van-field__error-message` 行内红字展示（`toast=0, inline=1`）。场景 CSV 期望仅要求「提示『请输入位置名称』且不发请求」，故判定 PASS；但与本页其他反馈（保存/删除/上传均走 toast）形式不一致，建议统一口径。
2. **弹层打开动画期截图**：`lstep-08`、`lstep-14` 捕获到弹层上升动画的中间帧（弹层顶边分别位于 y≈471 / y≈637，未到最终位置 y≈241）。Playwright 的 `toBeVisible` 只要求元素有布局盒，因此断言通过但截图非「稳定态」。属证据时序问题，非应用缺陷；如需稳定截图可在断言后加 `waitForTimeout(400)`。
3. **子级缩进量较弱**：depth 缩进为 `12px → 28px`（差 16px），在 375 宽视口下可辨识但不明显；CSV 已明确该数值，故判定 PASS。若产品期望更强的层级感可加大缩进或加层级前缀。
4. **保存后弹层关闭时序正常**：探针确认新增/编辑两条路径保存成功后弹层均在 ~800ms 内 `display:none`，不会残留打开状态。

## 覆盖缺口（本轮未覆盖，建议后续补）

| # | 缺口 | 建议 |
|---|---|---|
| 1 | 多级位置（≥3 层）与「改父级到根节点」回退、把父级改为其后代（防环）校验 | 补 E-LM-11 场景 |
| 2 | 位置名称重复 / 超长（>50 字符）的前后端校验与提示 | 补边界用例 |
| 3 | 说明 500 字**保存后**是否完整回显（本轮只测输入截断） | 补回显断言 |
| 4 | `webp` 上传（错误文案声明支持但未实测）；编辑时**删除照片**后 `imageUrl` 是否清空、列表缩略是否消失 | 补 08f/08g |
| 5 | 上传成功但点「取消/不保存」→ 服务器临时文件是否残留（磁盘泄漏面） | 结合 api-tester 查 `/uploads` 目录 |
| 6 | MEMBER 角色对 `/locations` 增删改的权限（页面级）与前端路由守卫 | 与 api-tester RBAC 用例对齐 |
| 7 | 弱网 / 上传中断 / 后端 5xx 时前端错误提示 | 需 route mock 才可稳定复现 |
| 8 | 其他视口（320px 窄屏、768 平板、横屏）与深色模式 | 响应式补充轮 |
| 9 | 连点「保存」防重复提交（同名位置重复创建） | 补并发用例 |
| 10 | 删除确认弹窗的「取消」分支（本轮只走「确认」） | 一行断言即可补 |

## 既有回归复跑（同一隔离库、同一时段）

| 轮次 | 脚本 | 结果 | 证据 |
|---|---|---|---|
| 全量主流程 | `family-storage.spec.js` | **1 passed（35.5s，47 张 step 截图）** | `evidence-2026-09-28-1558-regress/`（`recording.webm`、`trace.zip`） |
| 防御分支 | `family-storage-defenses.spec.js` | **1 passed（32.5s，14 张 dstep 截图）** | `evidence-2026-09-28-1559-regress-defenses/` |

回归与新场景互不污染：新场景先跑（自清理），回归后跑（沿用 `e2eadmin`）。

## 测试侧修正记录（非应用缺陷，留存审计）

- attempt1（15:49）：断言 `.van-cell__title > span` 取词不匹配 Vant 结构 → 改为按可见文本 + `.van-cell__label` 断言（E-LM-01 注册成功证据即出自该轮）。
- attempt2（15:55）：断言 HTML `maxlength` 属性失败（Vant 不渲染该属性，内部 JS 截断）→ 改为行为断言（`0/500` → 输入 520 字截断为 500 → 清空回 `0/500`）。
- attempt3（15:57）：16/16 全绿，采纳为主证据。

## 复现方式

```powershell
# 1) 确保前后端已启动（5173 / 8080），隔离库 family_storage_e2e 可用
# 2) 运行（PowerShell 5.1，先设 UTF-8 与证据目录）
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:EVIDENCE_DIR = "D:\xcode\wt\.worktrees\family-storage-system\docs\e2e\evidence-<新时间戳>"
Set-Location 'C:\Users\Administrator\AppData\Local\Temp\opencode\e2e-pw'
& .\node_modules\.bin\playwright.cmd test `
  D:\xcode\wt\.worktrees\family-storage-system\docs\e2e\scripts\family-storage-locations.spec.js `
  --config=playwright.config.js
```

> 注意：隔离库已有数据时，E-LM-01 走「注册关闭 → 登录回退」分支（脚本已内置），需以 `e2eadmin / E2eNew123!`（回归已改密；全新库为 `E2eTest123!`）登录。脚本只在空库首轮证明「首账号注册成功」。

## 产物与数据状态

- 新增文件：`docs/e2e/scenarios-location-management.csv`、`docs/e2e/scripts/family-storage-locations.spec.js`、3 个新 evidence 目录、本报告。
- 测试残留（隔离库 `family_storage_e2e`，非本页新场景产生）：
  - 分类 `日用品`（E-LM-09 前置创建，保留供复跑幂等）；
  - 回归遗留数据（用户 4 / 位置 2 / 物品 3 / 分类 2 等）；
  - `e2eadmin` 密码现为 `E2eNew123!`（全量回归改密所致）。
- 本页新场景自身数据已清空（位置 0、照片文件 2 个均 `DELETE` 成功 204、探针临时位置已删除）。
- 未修改任何应用代码；未落盘任何真实凭据（仅测试口径密码，见场景 CSV 说明）。
