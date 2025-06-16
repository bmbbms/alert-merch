# 构建阶段
FROM registry.jlpay.com/middleware/golang-ora:1.23.0 AS builder

WORKDIR /app

RUN go env -w GO111MODULE=on; \
    go env -w GOPROXY=https://goproxy.cn,direct


# 安装必要的构建工具
#RUN apk add --no-cache git jq

# 复制go.mod和go.sum
COPY go.mod ./

# 下载依赖
RUN go mod download

# 复制源代码
COPY . .

# 构建应用
RUN CGO_ENABLED=1 GOOS=linux go build -o alert-merch main.go

# 运行阶段
FROM registry.jlpay.com/middleware/golang-ora:1.23.0

WORKDIR /app

# 安装必要的运行时依赖a
# RUN set -eux; \
#         sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories;\
#         apk add --no-cache ca-certificates tzdata jq

# 从构建阶段复制二进制文件
COPY --from=builder /app/alert-merch .
# 复制配置文件
# COPY --from=builder /app/configs/config.yaml ./configs/
# COPY --from=builder /app/templates/backlog.html ./templates/

# 设置时区
ENV TZ=Asia/Shanghai

# 暴露端口
EXPOSE 8080

# 设置健康检查
#HEALTHCHECK --interval=30s --timeout=3s \
#  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

# 启动应用
CMD ["./alert-merch"] 
