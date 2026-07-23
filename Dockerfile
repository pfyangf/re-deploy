# syntax=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/docker/dockerfile:1.6
#
# re-deploy server 镜像
#
# 前置条件：宿主机必须先跑 ./scripts/build.{ps1,sh} 生成以下产物：
#   - server/target/redeploy-server-*.jar
#   - server/data/agents/deploy-agent-linux-amd64
#   - server/data/agents/deploy-agent-linux-arm64
#
# 不要直接 `docker build .`，请通过 scripts/build 或 scripts/release 触发。
#
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:17-jre-alpine

# 基础工具 + 时区
RUN apk add --no-cache tzdata bash curl \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

WORKDIR /app

# 应用 jar
COPY server/target/redeploy-server-*.jar /app/redeploy-server.jar

# 分发用的 agent 二进制（AgentDownloadController 会从 /app/data/agents/ 读）
COPY server/data/agents/deploy-agent-linux-amd64 /app/data/agents/deploy-agent-linux-amd64
COPY server/data/agents/deploy-agent-linux-arm64 /app/data/agents/deploy-agent-linux-arm64

# 运行时可写目录
RUN mkdir -p /app/data/uploads /app/logs \
    && chmod +x /app/data/agents/deploy-agent-linux-amd64 /app/data/agents/deploy-agent-linux-arm64

VOLUME ["/app/data", "/app/logs"]

EXPOSE 9006

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://127.0.0.1:9006/ >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/redeploy-server.jar"]
