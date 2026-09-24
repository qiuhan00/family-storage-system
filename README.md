# 家庭收纳物品管理系统

面向单个家庭的云服务器部署 Web 应用：家人通过浏览器（移动优先）记录、检索、管理家中物品的位置与数量，跟踪临期物品、维护缺耗品购物清单，并查看价值 / 盘点统计。前后端分离单体仓库，生产采用**非容器部署**（JDK 运行后端 jar + nginx 托管前端与反代）。

## 功能清单

- **物品管理**：物品 CRUD、名称模糊检索、按分类 / 位置筛选、临期筛选、物品照片上传与详情展示
- **分类 / 位置**：物品分类；收纳位置支持多级层级（父子树），列表按 sortOrder 排序
- **出入库**：入库 / 出库 / 盘点并留变更历史（`stock_changes` 审计），出入库与当前存量分离
- **临期提醒**：每日定时扫描临期物品生成站内通知（阈值 `expiry.remind.days` 可调，默认 7 天）；
  短信通道为预留扩展点（`NotificationChannel` 接口 + `sms.enabled` 开关），**v1 未接入任何短信厂商**
- **缺耗品购物清单**：常用品缺失随手记，勾选标记已购，按 PENDING / BOUGHT / CANCELLED 维度展示
- **通知**：站内通知分页、未读数、单条已读 / 全部已读（首页角标）
- **统计**：总览（物品数 / 总数量 / 总值 / 临期数 / 待购数）、按分类 / 按位置聚合
- **成员管理（ADMIN）**：成员列表分页、创建、更新、启用 / 停用（**无删除端点**，以停用代替）、重置密码；
  首个注册账号自动成为 ADMIN，系统非空后开放注册关闭，由 ADMIN 创建成员

所有人（ADMIN / MEMBER）对物品、分类、位置、购物清单、通知都可读写（家庭协作）；仅 ADMIN 可管理用户、删除分类 / 位置、修改应用设置。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.3.5 · Spring Data JPA · Spring Security（JWT 无状态认证 + BCrypt）· Flyway · springdoc(OpenAPI) |
| 数据库 | MySQL 5.7（本地）/ MySQL（生产），utf8mb4；索引受 5.7 前缀长度限制（名称列 `VARCHAR(100)` + 前缀索引） |
| 前端 | Vue 3 · TypeScript · Vant（移动优先）· Pinia · Vue Router · Vite · Axios |
| 测试 | JUnit（后端单测）· Vitest（前端单测）· Playwright（E2E）· api-tester（接口自动化） |

## 目录结构

```
backend/           Spring Boot 3 后端（Maven，Flyway 迁移在 src/main/resources/db/migration）
frontend/          Vue 3 前端（Vite dev 代理 /api 与 /uploads → 8080）
deploy/            start.sh 生产启动脚本 + nginx-family-storage.conf 反代样例
docs/              api-spec.md 接口契约 · reports/api-test-report.md 接口测试报告 ·
                   e2e/ Playwright E2E 报告与证据 · superpowers/ 设计文档与计划
.env.example       环境变量模板（真实值不入库）
```

## 本地开发快速开始

前置：JDK 17、Maven 3.8+、Node.js 18+、本机 MySQL 5.7（服务名 MySQL57）可用。

1. **建库**（后端由 Flyway 自动建表，无需手建）：
   ```sql
   CREATE DATABASE family_storage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. **后端启动**（端口 8080），数据库口令经环境变量 `DB_PASSWORD` 传入，缺失默认 `root`：
   ```bash
   cd backend
   export DB_PASSWORD=你的本地MySQL口令      # Windows PowerShell: $env:DB_PASSWORD="..."
   mvn spring-boot:run
   ```
   连接默认指向 `jdbc:mysql://localhost:3306/family_storage`，可用环境变量 `DB_URL` 覆盖；用户名默认 `root`。
3. **前端启动**（新终端，Vite 5173，自动代理 `/api`、`/uploads` 到 8080）：
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
4. 浏览器打开 `http://localhost:5173`，在注册页创建**第一个账号**即 `ADMIN`；
   系统非空后注册页关闭，后续成员由 ADMIN 在「成员管理」中创建。

## 测试

- 后端单测：`cd backend && mvn test`（58 用例全绿）
- 前端单测：`cd frontend && npm run test`（即 `vitest run`，20 用例全绿）
- 集成验证产物（已落盘，可直接查阅）：
  - 接口自动化 `docs/reports/api-test-report.md`：79/79 通过
  - 前端 E2E `docs/e2e/e2e-report.md`：Playwright 46 步全流程通过（截图 / 视频 / trace 证据见 `docs/e2e/evidence-*`）

## 生产部署

非容器部署手册见 **`docs/deploy.md`**（JDK17 + MySQL 5.7 + nginx；`deploy/start.sh` 与 `deploy/nginx-family-storage.conf` 为可复用样例）。

## 敏感信息约定

密钥一律走**环境变量**注入，不硬编码、不入仓库：`DB_PASSWORD`、`JWT_SECRET`（`>=32` 字节强随机串，生成示例 `openssl rand -base64 48`）、`DB_URL`、`UPLOAD_DIR`。
环境变量清单见 `.env.example`（含生产示例值格式）。