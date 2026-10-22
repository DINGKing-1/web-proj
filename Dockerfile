# ========== 优品汇 · 腾讯云托管 Dockerfile ==========
# 多阶段：前端构建 → 后端打包 → JRE 运行
# 云托管控制台建议：运行命令留空（用 ENTRYPOINT），端口填 8080（或注入 PORT 环境变量）

# ---------- Stage 1: 前端 ----------
FROM node:20-alpine AS frontend
WORKDIR /app/frontend

# 高德 Key 等构建期变量（可选）：docker build --build-arg VITE_AMAP_KEY=xxx
ARG VITE_AMAP_KEY=
ARG VITE_AMAP_SECURITY_JS_CODE=
ENV VITE_AMAP_KEY=${VITE_AMAP_KEY} \
    VITE_AMAP_SECURITY_JS_CODE=${VITE_AMAP_SECURITY_JS_CODE}

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# vite outDir = ../src/main/resources/static
RUN mkdir -p /app/src/main/resources/static \
  && npm run build

# ---------- Stage 2: 后端 ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app

COPY pom.xml ./
# 先拉依赖层缓存
RUN mvn -q -B dependency:go-offline

COPY src ./src
# 覆盖静态资源为前端构建产物
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static

RUN mvn -q -B clean package -DskipTests

# ---------- Stage 3: 运行 ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 非 root 运行
RUN useradd -r -u 1001 mall \
  && mkdir -p /app/product-images /app/user-avatar \
  && chown -R mall:mall /app

COPY --from=backend /app/target/mall-*.jar /app/app.jar

USER mall

# 云托管注入 PORT；默认 8080
ENV PORT=8080 \
    JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=pg

EXPOSE 8080

# 优雅关机：云托管发 SIGTERM 后 Spring graceful shutdown 生效
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
