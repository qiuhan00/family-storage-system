# 家庭收纳物品管理系统 — 生产部署手册（非容器）

本项目在生产环境采用**非容器部署**（用户裁决）：云服务器直接运行后端 `jar`（JDK 17）+ nginx 托管前端 `dist` 并反代 `/api`、`/uploads`。不使用 Docker / 容器编排。

前置产物：

- 后端：`backend/target/storage-0.0.1-SNAPSHOT.jar`（`cd backend && mvn clean package`）
- 前端：`frontend/dist/`（`cd frontend && npm run build`）

## 1. 服务器要求

| 组件 | 要求 |
|---|---|
| 操作系统 | 任意支持 JDK 17 的 Linux 发行版均可（下文以 Debian/Ubuntu 系为例） |
| JDK | 17（`sudo apt install openjdk-17-jre-headless`） |
| MySQL | 5.7 及以上（本仓库已按 5.7 兼容编写，索引遵循 utf8mb4 前缀限制） |
| nginx | `sudo apt install nginx` |

## 2. 建库与运行环境变量

1. 建库（utf8mb4）：
   ```sql
   CREATE DATABASE family_storage
     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   后端通过 Flyway 自动执行迁移建表，无需手工建表。

2. 准备环境变量，**真实值一律经启动环境注入，不入仓库、不入代码**。清单见 `.env.example`：

   | 变量 | 必填 | 说明 |
   |---|---|---|
   | `DB_URL` | 是 | 例如 `jdbc:mysql://localhost:3306/family_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai` |
   | `DB_PASSWORD` | 是 | MySQL 数据库口令，`start.sh` 校验非空才启动 |
   | `JWT_SECRET` | 是 | JWT 签名密钥，**必须 ≥32 字节**强随机串，生成示例：`openssl rand -base64 48` |
   | `UPLOAD_DIR` | 否 | 上传文件根目录，默认 `./uploads`；需与 nginx `/uploads` alias 目录一致 |

   建议作为独立低权限用户（如 `family-storage`）运行，密钥文件设 `chmod 600`。

## 3. 启动后端（prod profile）

可直接使用样例脚本 `deploy/start.sh`（它完成 JWT_SECRET / DB_PASSWORD 非空校验并以 prod 启动）：

```bash
chmod +x deploy/start.sh
JWT_SECRET=<强随机串>=32字节> DB_PASSWORD=<口令> \
DB_URL='jdbc:mysql://localhost:3306/family_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
UPLOAD_DIR=/var/www/family-storage/uploads \
./deploy/start.sh        # jar 默认取 ./app.jar，可用 JAR=/path/to/storage-0.0.1-SNAPSHOT.jar 覆盖
```

或直接执行：

```bash
java -jar /opt/family-storage/storage-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

后台运行：`nohup ./deploy/start.sh > /var/log/family-storage.log 2>&1 &`。

### systemd 参照

`deploy/start.sh` 头部附有完整 systemd unit 样例（`EnvironmentFile=/etc/family-storage.env` 存放环境变量），核心如下：

```ini
[Unit]
Description=family-storage backend

[Service]
Type=simple
User=family-storage
WorkingDirectory=/opt/family-storage
EnvironmentFile=/etc/family-storage.env
ExecStart=/opt/family-storage/start.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

`start.sh` 只读环境、不落盘密钥；配合 `chmod 600` 的 `EnvironmentFile` 即可安全保存密钥。

## 4. 部署前端与 nginx

1. 上传 `frontend/dist/` 到服务器，如 `/var/www/family-storage/dist`。
2. 复制样例配置并改绝对路径：
   ```bash
   sudo cp deploy/nginx-family-storage.conf /etc/nginx/sites-available/family-storage
   sudo ln -s /etc/nginx/sites-available/family-storage /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   ```
   配置要点（`deploy/nginx-family-storage.conf` 已内嵌注释）：
   - **root** 指向 `dist`，`try_files $uri $uri/ /index.html`（SPA history 路由回退）
   - `location /api` → `proxy_pass http://127.0.0.1:8080`（透传 Host / X-Forwarded-*）
   - `location /uploads` → **alias** 到后端 `UPLOAD_DIR` 同值目录（如 `/var/www/family-storage/uploads`），并加 `expires 30d` 静态缓存
   - `client_max_body_size 6m`（与后端 multipart 上限对齐）
   - gzip 建议开启：`gzip on; gzip_types text/plain text/css application/javascript application/json image/svg+xml;`
3. **HTTPS（可选，建议）**：用 certbot 自动签发并改写配置：
   ```bash
   sudo apt install certbot python3-certbot-nginx
   sudo certbot --nginx -d your.domain.example
   ```
   certbot 会加入 443 / ssl_certificate 并保留 80→443 跳转（占位 `server_name` 需先改为真实域名）。

## 5. 首次注册创建 ADMIN

部署完成后打开站点 → 注册页创建**第一个账号**，该账号自动成为 `ADMIN`。

- 系统非空后注册关闭（后端返回 `403 REGISTRATION_CLOSED`），后续成员由 ADMIN 在「成员管理」中创建。
- 成员「启停」代替删除（无删除端点）；停用即禁止登录。

## 6. 备份与升级注意

**备份**：

- 数据库：`mysqldump` 定时导出 `family_storage`（数据唯一的权威来源）。
- 上传目录：`UPLOAD_DIR` 独立备份（物品照片），与数据库保持一致备份周期。
- 密钥：`JWT_SECRET`、`DB_PASSWORD` 的 `EnvironmentFile` 单独保存（丢失将导致 token 失效、无法连接数据库）。

**升级**：

- 后端起 `--spring.profiles.active=prod` 时会启动时检查并**自动执行新的 Flyway 迁移**（`baseline-on-migrate=true`）；升级前先备份数据库，迁移失败则回滚发行版本。
- 前端升级仅替换 `dist/` 内容并 `sudo systemctl reload nginx`，不影响运行中的数据。
- 涉及 schema 变更的版本，迁移后立即抽查关键端点（见 `docs/api-spec.md`）。