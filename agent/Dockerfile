# syntax=docker/dockerfile:1.6

# ---- build stage ----
FROM golang:1.21-alpine AS builder

WORKDIR /src

# 依赖缓存层。仓库当前没有 go.sum；若已 `go mod tidy` 生成，会一并被 COPY 走。
COPY go.mod go.su[m] ./
RUN go mod download || true

COPY . .

ARG TARGETOS=linux
ARG TARGETARCH=amd64
ENV CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH}

RUN go build -trimpath -ldflags="-s -w" -o /out/deploy-agent ./cmd/agent

# ---- runtime stage ----
FROM alpine:3.19

RUN apk add --no-cache bash curl ca-certificates tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

ENV TZ=Asia/Shanghai \
    AGENT_CONFIG_DIR=/opt/deploy-agent/conf

RUN mkdir -p /opt/deploy-agent/conf /opt/deploy-agent/data

COPY --from=builder /out/deploy-agent /usr/local/bin/deploy-agent

VOLUME ["/opt/deploy-agent/conf", "/opt/deploy-agent/data"]

EXPOSE 9009

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS http://127.0.0.1:9009/api/health || exit 1

ENTRYPOINT ["/usr/local/bin/deploy-agent"]
