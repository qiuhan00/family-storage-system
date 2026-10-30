# 前端镜像：多阶段构建（Node 构建 dist → nginx 静态托管 + /api 反代）。
ARG REGISTRY=docker.io

FROM ${REGISTRY}/library/node:24-slim AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM ${REGISTRY}/library/nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/nginx.docker.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
