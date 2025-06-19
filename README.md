# 商户入网审核流程监控系统

## 功能特性

### 持久化功能
系统现在支持超时任务数据的持久化存储，确保服务重启后能够恢复之前的状态：

- **自动保存**: 程序每10分钟自动保存超时任务数据到本地文件
- **优雅关闭**: 收到 SIGINT 或 SIGTERM 信号时，程序会先保存数据再退出
- **启动恢复**: 程序启动时自动从文件加载之前保存的超时任务数据
- **可配置路径**: 支持通过环境变量配置持久化文件存储路径

### 环境变量配置

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `PERSIST_PATH` | `.` (当前目录) | 持久化文件存储路径 |
| `ORACLE_DSN` | - | Oracle数据库连接字符串 |
| `WECOM_WEBHOOK` | - | 企业微信群机器人Webhook地址 |
| `WECOM_WEBHOOK2` | - | 第二个企业微信群机器人Webhook地址 |
| `WECOM_WEBHOOK3` | - | 第三个企业微信群机器人Webhook地址 |
| `TASK_TIMEOUT_MINUTES` | 3 | 任务超时时间（分钟） |
| `CHECK_INTERVAL_SECONDS` | 60 | 检查间隔时间（秒） |
| `UNFINISHED_TIMEOUT_MINUTES` | 10 | 未完成任务超时时间（分钟） |
| `HEALTH_PORT` | 8080 | 健康检查服务端口 |

### 持久化文件
- `timeout_tasks.json`: 存储超时未领取的任务数据
- `timeout_finish_tasks.json`: 存储超时未完成的任务数据

### 数据格式
持久化文件使用 JSON 格式存储，包含以下信息：
- 任务ID
- 创建时间
- 任务类型（unclaimed/unfinished）

## 部署说明

### 环境配置
复制 `config.example` 文件为 `.env` 文件，并根据实际情况修改配置：

```bash
cp config.example .env
# 编辑 .env 文件，填入实际的配置值
```

### Docker 部署
如果使用 Docker 部署，建议将持久化文件目录挂载到宿主机：

```bash
# 使用默认路径（当前目录）
docker run -v /path/to/data:/app -e ORACLE_DSN="..." alert-merch

# 使用自定义路径
docker run -v /path/to/data:/app/data -e PERSIST_PATH="/app/data" -e ORACLE_DSN="..." alert-merch
```

### Kubernetes 部署
在 Kubernetes 中，建议使用 PersistentVolume 来存储持久化数据：

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: alert-merch-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

然后在 Deployment 中挂载：

```yaml
env:
- name: PERSIST_PATH
  value: "/app/data"
volumes:
- name: data-volume
  persistentVolumeClaim:
    claimName: alert-merch-data
volumeMounts:
- name: data-volume
  mountPath: /app/data
```

## 注意事项

1. 持久化文件包含敏感的任务信息，请确保文件权限设置正确
2. 建议定期备份持久化文件
3. 程序启动时会自动创建持久化目录和文件（如果不存在）
4. 如果持久化文件损坏，程序会使用空数据启动并记录错误日志
5. 如果 `PERSIST_PATH` 环境变量未设置，程序将使用当前工作目录存储持久化文件 