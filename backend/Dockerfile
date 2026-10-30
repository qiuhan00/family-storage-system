# 后端镜像：多阶段构建（Maven 打包 → JRE 运行）。
# REGISTRY 可切换镜像源：docker.io（默认）或镜像站如 docker.m.daocloud.io。
ARG REGISTRY=docker.io

FROM ${REGISTRY}/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
RUN mvn -B -q -DskipTests dependency:go-offline || true
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM ${REGISTRY}/library/eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
 && mkdir -p /app/uploads && chown -R app:app /app
COPY --from=build /app/target/storage-0.0.1-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
