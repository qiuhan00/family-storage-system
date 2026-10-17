# 便签纸视觉重设计 — E2E 验证报告

- **日期**：2026-10-17
- **方式**：Playwright MCP 交互式驱动（真机浏览器）+ `getComputedStyle` 断言
- **前端**：Vite dev `http://localhost:5173`（`/api`、`/uploads` 代理到 8080）
- **后端**：Spring Boot jar（JDK17），指向**临时空库** `family_storage_mcp`
- **视口**：MCP 浏览器默认视口（截图为 929×869）
- **结果**：**PASS**（功能不回退；关键样式以计算值验证；仅 `favicon.ico` 404）

## 覆盖流程

| # | 场景 | 结果 | 证据 |
|---|---|---|---|
| 1 | 未登录访问 `/` → `/login`；纸底 + 衬线标题 + 墨色主按钮 | PASS | 01-login.png |
| 2 | 注册首个账号 → 自动登录首页（纸卡统计、临期便签、Tabbar） | PASS | 10-home-data.png |
| 3 | 物品列表：纸卡 + 贴纸标签（食品 / 苔绿位置）+ 陶土临期 | PASS | 05-items-list.png |
| 4 | 物品表单：纸卡分组、虚线分隔、虚线上传框、墨色提交 | PASS | 04-item-form.png |
| 5 | 物品详情：主便签 + 陶土临期戳 + 字段网格 + 库存小纸条 | PASS | 06-item-detail.png |
| 6 | 购物清单：纸卡 + 待购陶土 chip | PASS | 07-shopping.png |
| 7 | 个人中心：纸卡、墨色头像、菜单组、砖红描边退出 | PASS | 08-profile.png |
| 8 | 应用设置：纸卡、Stepper、墨色保存；文案「临期物品…」 | PASS | 09-settings.png |

## 计算样式断言（修复后实测）

| 项 | 期望 | 实测 |
|---|---|---|
| 登录主按钮背景 / 文字 | `#2B2622` / `#F6F1E7` | `rgb(43,38,34)` / `rgb(246,241,231)` |
| 按钮圆角 | 2px | `2px` |
| 占位文字颜色 | `--ink-2 #6E655A` | `rgb(110,101,90)` |
| NavBar / Tabbar 背景 | `#F6F1E7` | `rgb(246,241,231)` |
| 表单纸卡背景 / 圆角 | `#FFFDF8` / 3px | `rgb(255,253,248)` / `3px` |
| 单元格分隔线 | dashed | `dashed` |
| 搜索框背景 / 圆角 | 纸卡 / 2px | `rgb(255,253,248)` / `2px` |
| 统计纸卡倾斜 | 非单位矩阵 | `matrix(0.99996,-0.00873,...)` |
| 临期文字颜色 | `#A34A2B`（≥4.5:1） | `rgb(163,74,43)` |
| 小按钮高度（筛选/新增） | ≥44px | `44` |
| Stepper 按钮 | ≥44px、纸底 | `44×44`、`rgb(246,241,231)` |
| 弹层 / Dialog 背景 | `#FFFDF8` | `rgb(255,253,248)` |
| Radio 选中 / Calendar 选中日 | `#2B2622` | `rgb(43,38,34)`（Calendar 文字为纸色） |
| 首页临期条目 | 键盘可聚焦 | `<button>`、`tabIndex=0` |

## 验证中发现并修复的缺陷（最终评审后一轮修复）

1. **Vant 主题变量未落到组件**：组件级变量在 `:root` 按默认主色解析，ConfigProvider 无法回灌，导致 Radio/Calendar/Dialog 仍为默认蓝。修复：用 `html:root` 覆写源变量与组件变量（teleport 到 body 的弹层同样生效）。
2. **全局覆盖被 Vant 懒加载样式压过**：NavBar/Tabbar 白底、表单 8px 白卡、占位文字 1.66:1、搜索胶囊、分隔实线。修复：结构覆盖加 `#app` / `body` 前缀提高特异性；补充 placeholder/empty/list 颜色变量。
3. **`plain` 主按钮被填充**：`:not(.van-button--plain)` 区分填充/描边。
4. **胶囊圆角**：统一 2px。
5. **陶土色文字对比度不足（3.7:1）**：新增 `--terracotta-ink #A34A2B`（实测 5.2:1），仅用于文字；描边/图标仍用 `--terracotta`。贴纸按钮底色改为 `--terracotta-ink`（白字 5.5:1）。
6. **触摸目标不足**：小按钮 `min-height:44px`，Stepper 按钮 44×44，Switch 扩大点击区。
7. **首页临期条目键盘不可达**：改为 `<button type="button">`。
8. **微旋转被入场动画覆盖**：`paper-drop` 改用独立 `translate` 属性，保留 `transform: rotate()`。

## 控制台

整段会话仅 1 条错误：`favicon.ico` 404（Vite dev，纯装饰）。**无未捕获 JS 异常**。

## 设计一致性

- 纸底 `#F6F1E7` + 点阵纹理、纸卡 `#FFFDF8`、便签黄仅用于临期、墨色文字、陶土/苔绿/砖红语义色按 spec 生效。
- 标题自托管 Noto Serif SC（`h1` / NavBar 计算字体为 `"Noto Serif SC", ...`），正文系统字体，`font-display: swap` 有回退。
- SVG 线性图标替换结构图标与 emoji；无 canvas/emoji 结构图标。
- `prefers-reduced-motion` 全局规则保留；纸卡内容不依赖动画完成才可见。

## 证据

`01-login`、`04-item-form`、`05-items-list`、`06-item-detail`、`07-shopping`、`08-profile`、`09-settings`、`10-home-data`（PNG，本目录）。

## 收尾（已执行）

- 关闭 Playwright MCP 浏览器，删除 `%LOCALAPPDATA%\ms-playwright-mcp`、`%TEMP%\playwright-artifacts-*`、`playwright-transform-cache`；保留 `%LOCALAPPDATA%\ms-playwright`。
- 停止 8080 / 5173 服务；`DROP DATABASE family_storage_mcp`。
- 数据库指向未持久化改动（测试用进程级 `DB_URL`），默认仍为 `family_storage`。
