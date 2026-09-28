# 证据索引 · 防御分支回归复跑（2026-09-28 16:00 运行）

- 结果：**1 passed / 0 failed（32.5s）** · 脚本 `docs/e2e/scripts/family-storage-defenses.spec.js`
- 视口 1280×720 · Chrome headless · 依赖前序全量回归后的库状态（`e2eadmin` 密码已为 `E2eNew123!`）
- 覆盖：成员停用 401 登出跳转、ADMIN 自停用拦截、登录连错 5 次 429、假 magic link 拒绝、jpeg 上传缩略、末次管理员前端存活、绿色复核
- 内容：`dstep-01..14-*.png`（14 张）、`recording.webm`、`trace.zip`、`test-results/`（status=passed）
- 控制台摘要：401×8 / 429 / 400 / favicon 404 均为预期分支，无 pageerror、无 5xx、无 failed 请求
- 完整报告：`docs/reports/e2e-report-2026-09-28-location-management.md`（回归复跑小节）
