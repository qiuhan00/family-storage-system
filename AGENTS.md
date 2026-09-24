# AGENTS.md

本仓库当前**没有应用代码**：无 README / manifest / git 历史，唯一内容是 `.opencode/` 下的智能体团队与技能。不要寻找入口点，也不要臆造构建/测试命令。

## 工作流（智能体团队）

- `architect`（primary，主规划）：接收需求 → 产出技术方案、接口规范（`docs/api-spec.md`）、数据库设计 → 用 Task 分派任务，最后汇总验收。
- `backend` / `frontend`（all）：分别写后端 / 前端代码；完成后用 Task 调用 `api-tester` / `ui-tester` 子 Agent 验证，按缺陷修复并复测。
- `api-tester` / `ui-tester`（subagent）：只能被其他 Agent 用 Task 调用，不可直接选中。分别跑接口自动化测试、Playwright 页面 E2E（截图/视频证据），输出报告并反馈缺陷。
- `code-reviewer`（all）：代码 + 测试都完成后再做质量与安全评审，分级输出修复建议。

## 关键约定

- 契约先行：编码前先由 architect 定下接口与数据模型；编码 Agent 不得擅改契约或臆造字段。
- 验证方式固定：后端验收用 `api-tester`，前端验收用 `ui-tester`（Playwright），不要凭空选测试框架。
- 技能映射：`.opencode/skills/` 下已装 `api-tester`（接口测试执行规范）、`e2e-test`（Playwright E2E，供 ui-tester 加载）、`fullstack-coder`（全栈实现规范）。子 Agent 先用 `/skill` 加载对应技能再执行。
- 敏感信息：一律走环境变量，禁止硬编码密钥；用 Task 传参给子 Agent 时只放非敏感信息。
- 评审产物（接口规范、测试报告、文档）落盘到 `docs/`，保留可追溯证据。

## 注意

- 没有 CI / lint / typecheck 脚本；交付质量 = 子 Agent 测试 + code-reviewer 评审。
- 变更 `.opencode/` 下的 agent / skill 文件后需重启 opencode 才生效。