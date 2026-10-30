# Docker 部署（自包含）

一条命令起 MySQL 5.7 + 后端 + 前端/nginx，对外只暴露 **18088**。

## 前置

- Docker + Docker Compose v2
- 若所在网络无法直连 Docker Hub，可在 `.env` 设置镜像站：
  `DOCKER_REGISTRY=docker.m.daocloud.io`

## 配置

在仓库根目录创建 `.env`（已被 `.gitignore` 忽略）：

```dotenv
# MySQL root 口令（首次初始化后由数据卷保留，修改需重建 db_data 卷）
DB_PASSWORD=改成你的强口令

# JWT 签名密钥，>=32 字节；生成：openssl rand -base64 48
JWT_SECRET=改成至少32字节的强随机串

# 可选：镜像源（默认 docker.io）
DOCKER_REGISTRY=docker.m.daocloud.io
```

`docker compose` 会自动读取根目录 `.env` 做变量替换。

## 启动 / 停止

```bash
docker compose up -d --build     # 构建并后台启动
docker compose ps                # 查看状态
docker compose logs -f backend   # 看后端日志
docker compose down              # 停止（保留数据卷）
docker compose down -v           # 停止并删除数据卷（清库）
```

启动后访问：**http://<host>:18088**（首个注册账号自动成为 ADMIN）。

## 结构

| 服务 | 说明 | 端口 |
|---|---|---|
| `db` | MySQL 5.7，数据卷 `db_data` | 仅内网 3306 |
| `backend` | Spring Boot（prod profile），上传目录卷 `uploads` | 仅内网 8080 |
| `web` | nginx 静态托管前端 + 反代 `/api`、`/uploads` | 宿主 `18088` → 容器 80 |

## 说明

- 密钥只经环境变量注入，不入镜像、不入仓库。
- 上传文件持久化在 `uploads` 卷，与后端 `UPLOAD_DIR=/app/uploads` 一致。
- 前端镜像构建执行 `npm ci && npm run build`，后端镜像多阶段 Maven 打包。
- 生产 HTTPS/域名建议在外层再加一层反向代理（如 Caddy/Traefik 或云负载均衡）。
