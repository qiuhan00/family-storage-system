# 便签纸视觉重设计 — E2E 验证报告

- **日期**：2026-10-17
- **方式**：Playwright MCP 交互式驱动（真机浏览器）
- **前端**：Vite dev `http://localhost:5173`（`/api`、`/uploads` 代理到 8080）
- **后端**：Spring Boot jar（JDK17），指向**临时空库** `family_storage_mcp`
- **视口**：1280×720
- **结果**：**PASS**（核心流程功能不回退；仅 `favicon.ico` 404）

## 覆盖流程

| # | 场景 | 结果 | 证据 |
|---|---|---|---|
| 1 | 未登录访问 `/` → 重定向 `/login`；登录页纸感 + 衬线标题 + 墨色主按钮 | PASS | 01-login.png |
| 2 | 注册首个账号（paperadmin / 纸感管理员）→ 自动登录首页 | PASS | 02-register.png |
| 3 | 首页统计纸卡 + 临期空态 + 维度统计 + Tabbar 纸底 | PASS | 03-home-empty.png |
| 4 | 物品表单（纸卡、虚线分隔、上传虚线框、墨色提交） | PASS | 04-item-form.png |
| 5 | 新增物品（分类 Picker / 位置级联 / 日期日历）→ 列表显示纸卡 + 贴纸标签（食品/苔绿位置）+ 陶土临期 | PASS | 05-items-list.png |
| 6 | 物品详情：主便签（衬线名称 + 陶土临期戳）+ 字段网格 + 库存小纸条 + 墨色出入库/描边编辑/砖红删除 | PASS | 06-item-detail.png |
| 7 | 入库 +2 → 数量 5盒、历史「入库 +2」 | PASS | 06-item-detail.png |
| 8 | 购物清单新增「洗洁精」→ 待购陶土 chip；勾选已购 | PASS | 07-shopping.png |
| 9 | 个人中心：纸卡、头像墨色、菜单组、退出登录砖红描边 | PASS | 08-profile.png |
| 10 | 应用设置：纸卡、Stepper、墨色保存；文案已修正为「临期物品…」 | PASS | 09-settings.png |
| 11 | 首页有数据：统计值（1 件 / 5 / ¥29.50 / 1 项临期）+ 临期便签列表 | PASS | 10-home-data.png |

## 验证中发现并修复的缺陷

1. **Vant 主题变量未落到按钮**：组件级变量（如 `--van-button-primary-background`）在 `:root` 解析，ConfigProvider 只改 `--van-primary-color` 无法回灌 → 主按钮仍是默认蓝。修复：在 `#app` 重设组件级变量 + 提高直接覆盖选择器特异性。
2. **`plain` 主按钮被强制填充**：`.van-button--primary` 规则命中 plain 按钮导致文字不可见。修复：`:not(.van-button--plain)` 区分填充/描边。
3. **胶囊圆角**：Vant `round` 覆盖了 2px 纸感圆角。修复：`.van-button.van-button--round { border-radius: 2px }`。

修复后复测：主按钮为墨色 `#2B2622`，plain 描边按钮文字正常，圆角统一 2px。

## 控制台

整段会话仅 1 条错误：`favicon.ico` 404（Vite dev，纯装饰）。**无未捕获 JS 异常**，接口请求正常（无业务 4xx）。

## 设计一致性

- 纸底 `#F6F1E7` + 点阵纹理、纸卡 `#FFFDF8`、便签黄仅用于临期、墨色文字、陶土/苔绿/砖红语义色均按 spec 生效。
- 标题走自托管 Noto Serif SC（`h1` 计算字体为 `"Noto Serif SC", ...`），正文系统字体。
- Tabbar / 返回 / 通知 / 筛选 / 新增 / 编辑 / 删除 / 位置等均为统一 SVG 线性图标，无 emoji 结构图标。

## 证据

`01-login`、`02-register`、`03-home-empty`、`04-item-form`、`05-items-list`、`06-item-detail`、`07-shopping`、`08-profile`、`09-settings`、`10-home-data`（PNG，本目录）。

## 收尾（已执行）

- 关闭 Playwright MCP 浏览器，删除 `%LOCALAPPDATA%\ms-playwright-mcp`、`%TEMP%\playwright-artifacts-*`、`playwright-transform-cache`；保留 `%LOCALAPPDATA%\ms-playwright`。
- 停止 8080 / 5173 服务；`DROP DATABASE family_storage_mcp`。
- 数据库指向未持久化改动（测试用进程级 `DB_URL`），默认仍为 `family_storage`。
