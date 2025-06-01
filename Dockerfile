# 游戏服务器框架 - 多阶段构建 Dockerfile
# =============================================================================
# 基于 Eclipse Temurin 21 构建生产就绪的游戏服务器镜像
# 支持多阶段构建，优化镜像大小和构建速度
# =============================================================================

# 构建阶段 - 使用完整JDK进行编译
FROM eclipse-temurin:21-jdk-alpine AS builder

# 设置工作目录
WORKDIR /build

# 安装必要的工具
RUN apk add --no-cache \
    maven \
    curl \
    bash

# 设置Maven配置
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 复制项目配置文件（利用Docker缓存层）
COPY pom.xml .
COPY */pom.xml ./*/
COPY */*/pom.xml ./*/*/

# 下载依赖（这一层会被缓存，除非pom.xml发生变化）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY . .

# 构建应用
RUN mvn clean package -DskipTests -B -T 4

# 运行时阶段 - 使用精简JRE
FROM eclipse-temurin:21-jre-alpine AS runtime

# 设置标签
LABEL maintainer="liuxiao2015"
LABEL description="高性能分布式游戏服务器框架"
LABEL version="1.0.0"

# 创建应用用户（安全最佳实践）
RUN addgroup -g 1001 -S gameserver && \
    adduser -u 1001 -S gameserver -G gameserver

# 安装运行时依赖
RUN apk add --no-cache \
    curl \
    bash \
    tzdata \
    tini && \
    rm -rf /var/cache/apk/*

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/config /app/logs /app/data && \
    chown -R gameserver:gameserver /app

# 从构建阶段复制应用JAR
COPY --from=builder --chown=gameserver:gameserver /build/admin-console/target/admin-console-*.jar /app/app.jar

# 复制配置文件模板
COPY --chown=gameserver:gameserver docker/config/ /app/config/

# 复制启动脚本
COPY --chown=gameserver:gameserver docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# 切换到应用用户
USER gameserver

# 设置JVM参数
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -XX:+PrintGCDetails \
               -XX:+PrintGCTimeStamps \
               -Xloggc:/app/logs/gc.log \
               -XX:+UseGCLogFileRotation \
               -XX:NumberOfGCLogFiles=3 \
               -XX:GCLogFileSize=10M"

# 设置应用参数
ENV SPRING_PROFILES_ACTIVE=docker
ENV SPRING_CONFIG_LOCATION=classpath:/,file:/app/config/

# 暴露端口
EXPOSE 8080 8090 9090 9091

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8090/actuator/health || exit 1

# 使用tini作为init进程（处理信号和僵尸进程）
ENTRYPOINT ["/sbin/tini", "--"]

# 启动应用
CMD ["/app/entrypoint.sh"]