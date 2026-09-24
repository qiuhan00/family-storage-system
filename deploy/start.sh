#!/usr/bin/env bash
# 生产启动脚本（非容器部署）。
# 用法：
#   chmod +x deploy/start.sh
#   JWT_SECRET=<强随机串>=32字节> DB_PASSWORD=<口令> UPLOAD_DIR=/var/www/family-storage/uploads \
#     DB_URL='jdbc:mysql://localhost:3306/family_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
#     ./deploy/start.sh
# 说明：
#   * 不写死任何真实密钥——JWT_SECRET / DB_PASSWORD 必须以环境变量提供，缺失即拒绝启动。
#   * JWT_SECRET 生成示例（>=32 字节）：openssl rand -base64 48
#   * 建议以专用低权限用户运行（如 systemd DynamicUser 或独立账户）。
#
# 服务化建议（systemd）：
#   [Unit]  Description=family-storage backend
#   [Service] Type=simple
#             User=family-storage
#             WorkingDirectory=/opt/family-storage
#             EnvironmentFile=/etc/family-storage.env       # JWT_SECRET/DB_PASSWORD 等
#             ExecStart=/opt/family-storage/start.sh
#             Restart=on-failure
#   [Install] WantedBy=multi-user.target
#   EnvironmentFile 权限建议 chmod 600 root:root；本脚本同样只读取环境，不落盘密钥。
# 附件 gzip 后台的替代做法：
#   nohup ./start.sh > /var/log/family-storage.log 2>&1 &

set -u

JAR=${JAR:-app.jar}
if [ ! -f "$JAR" ]; then
  echo "ERROR: jar not found at $JAR (set JAR=/path/to/storage-0.0.1-SNAPSHOT.jar)" >&2
  exit 1
fi

if [ -z "${JWT_SECRET:-}" ]; then
  echo "ERROR: JWT_SECRET is required (>=32 bytes, e.g. openssl rand -base64 48). Refusing to start." >&2
  exit 1
fi

if [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "ERROR: JWT_SECRET must be at least 32 bytes, got ${#JWT_SECRET}. Refusing to start (weak key)." >&2
  exit 1
fi

if [ -z "${DB_PASSWORD:-}" ]; then
  echo "ERROR: DB_PASSWORD is required. Refusing to start." >&2
  exit 1
fi

# 显式透传环境变量，jar 以 prod profile 启动。
JWT_SECRET="$JWT_SECRET" DB_PASSWORD="$DB_PASSWORD" UPLOAD_DIR="${UPLOAD_DIR:-./uploads}" \
  exec java -jar "$JAR" --spring.profiles.active=prod