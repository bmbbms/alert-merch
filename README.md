# 商户入网审核流程监控系统

## 项目说明

本项目提供两个版本的实现：
- **Go版本** (`main.go`) - 轻量级实现
- **Java版本** (`src/main/java/`) - Spring Boot实现，功能更完整

## Java版本特性

### 技术栈
- **Spring Boot 2.7.18** - 主框架
- **OceanBase JDBC Driver 2.4.11** - OceanBase专用JDBC驱动
- **OceanBase Client 2.4.11** - OceanBase专用客户端
- **MyBatis** - 数据访问层（SQL映射）
- **Spring Data JPA** - 数据访问层（实体管理）
- **Spring Scheduling** - 定时任务
- **Apache HttpClient** - HTTP客户端
- **Jackson** - JSON处理
- **Lombok** - 代码简化

### 持久化功能
系统支持超时任务数据的持久化存储，确保服务重启后能够恢复之前的状态：

- **自动保存**: 程序每10分钟自动保存超时任务数据到本地文件
- **优雅关闭**: 收到关闭信号时，程序会先保存数据再退出
- **启动恢复**: 程序启动时自动从文件加载之前保存的超时任务数据
- **可配置路径**: 支持通过环境变量配置持久化文件存储路径

### 环境变量配置

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `OCEANBASE_JDBC_URL` | - | OceanBase JDBC连接字符串（支持负载均衡） |
| `OCEANBASE_USERNAME` | - | OceanBase用户名 |
| `OCEANBASE_PASSWORD` | - | OceanBase密码 |

### OceanBase连接配置

应用使用Spring Boot原生的数据源配置，支持OceanBase的负载均衡连接：

- **负载均衡** - 支持多节点负载均衡
- **连接池** - HikariCP连接池管理
- **健康检查** - 定期检查连接状态

#### 连接字符串格式

```properties
OCEANBASE_JDBC_URL=jdbc:oceanbase:loadbalance://@(NET_SERVICE_NAME=(DESCRIPTION=(OBLB=ON)(OBLB_RETRY_ALL_DOWNS=20)(OBLB_BLACKLIST=(REMOVE_STRATEGY=((NAME=TIMEOUT)(TIMEOUT=100)))(APPEND_STRATEGY=((NAME=RETRYDURATION)(RETRYTIMES=3)(DURATION=10000))))(ADDRESS_LIST=(OBLB_STRATEGY=SERVERAFFINITY)(ADDRESS=(PROTOCOL=tcp)(HOST=ob-lbproxy-m1.host)(PORT=2883))(WEIGHT=1)(ADDRESS=(PROTOCOL=tcp)(HOST=ob-lbproxy-m2.host)(PORT=2883))(WEIGHT=1)))(CONNECT_DATA=(SERVICE_NAME=SCHEDULER_USER))))&compatibleOjdbcVersion=8
```
| `WECOM_WEBHOOK` | - | 企业微信群机器人Webhook地址 |
| `WECOM_WEBHOOK2` | - | 第二个企业微信群机器人Webhook地址 |
| `WECOM_WEBHOOK3` | - | 第三个企业微信群机器人Webhook地址 |
| `TASK_TIMEOUT_MINUTES` | 3 | 任务超时时间（分钟） |
| `CHECK_INTERVAL_SECONDS` | 60 | 检查间隔时间（秒） |
| `UNFINISHED_TIMEOUT_MINUTES` | 10 | 未完成任务超时时间（分钟） |
| `HEALTH_PORT` | 8080 | 健康检查服务端口 |
| `PERSIST_PATH` | `.` | 持久化文件存储路径 |

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

### 本地开发运行

1. **使用启动脚本（推荐）**
```bash
chmod +x start.sh
./start.sh
```

2. **手动编译运行**
```bash
# 编译项目
mvn clean package

# 运行应用
java -jar target/alert-merch-1.0.0.jar
```

### Docker 部署

1. **构建镜像**
```bash
mvn clean package
docker build -f Dockerfile.java -t alert-merch-java .
```

2. **运行容器**
```bash
# 使用默认路径（当前目录）
docker run -v /path/to/data:/app/data -e ORACLE_DSN="..." alert-merch-java

# 使用自定义路径
docker run -v /path/to/data:/app/data -e PERSIST_PATH="/app/data" -e ORACLE_DSN="..." alert-merch-java
```

### Kubernetes 部署

1. **创建Secret**
```bash
kubectl create secret generic alert-merch-secret \
  --from-literal=oracle-dsn="your-oracle-connection-string" \
  --from-literal=wecom-webhook="your-webhook-url" \
  --from-literal=wecom-webhook2="your-webhook-url2" \
  --from-literal=wecom-webhook3="your-webhook-url3"
```

2. **部署应用**
```bash
kubectl apply -f k8s/pvc.yaml
kubectl apply -f k8s/deployment-java.yaml
kubectl apply -f k8s/service-java.yaml
```

### 健康检查

应用使用Spring Boot Actuator提供健康检查功能，包含以下端点：

#### Actuator端点
- `/actuator/health` - 总体健康状态
- `/actuator/health/liveness` - 存活检查
- `/actuator/health/readiness` - 就绪检查
- `/actuator/info` - 应用信息
- `/actuator/metrics` - 应用指标
- `/actuator/prometheus` - Prometheus指标（用于监控系统集成）

#### 自定义API端点
- `/api/status` - 应用状态信息（包含任务统计和配置信息）

#### 健康检查内容
- **Spring Boot Actuator** - 提供标准的健康检查端点
- **数据库连接检查** - 验证OceanBase连接状态
- **应用状态检查** - 检查超时任务服务运行状态

### Prometheus指标

应用提供以下Prometheus指标，可通过 `/actuator/prometheus` 端点获取：

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `task_total` | Counter | 新增入网总数（累计值，根据task_id去重统计） |
| `unclaimed_total` | Counter | 未领取总数（累计值，统计所有发现的未领取超时任务） |
| `unfinished_total` | Counter | 未完成总数（累计值，统计所有发现的未完成超时任务） |

#### 指标说明

- **task_total**: 统计所有新增的入网任务，每个task_id只统计一次，避免重复计数
- **unclaimed_total**: 统计所有发现的未领取超时任务，每个task_id只统计一次
- **unfinished_total**: 统计所有发现的未完成超时任务，每个task_id只统计一次

#### 使用示例

```bash
# 获取Prometheus指标
curl http://localhost:8080/actuator/prometheus

# 在Prometheus配置中添加抓取目标
scrape_configs:
  - job_name: 'alert-merch'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

#### Grafana仪表板示例

可以使用以下PromQL查询来创建Grafana仪表板：

```promql
# 新增入网总数
task_total

# 未领取总数
unclaimed_total

# 未完成总数
unfinished_total

# 每小时新增入网数（速率）
rate(task_total[1h]) * 3600
```

## 注意事项

1. 持久化文件包含敏感的任务信息，请确保文件权限设置正确
2. 建议定期备份持久化文件
3. 程序启动时会自动创建持久化目录和文件（如果不存在）
4. 如果持久化文件损坏，程序会使用空数据启动并记录错误日志
5. 如果 `PERSIST_PATH` 环境变量未设置，程序将使用当前工作目录存储持久化文件
6. Java版本需要JDK 11或更高版本
7. 确保OceanBase数据库连接字符串格式正确，支持Oracle模式

## 项目结构

```
alert-merch/
├── src/main/java/com/alert/merch/
│   ├── AlertMerchApplication.java          # 主启动类
│   ├── config/
│   │   ├── AppConfig.java                  # 应用配置
│   │   ├── MyBatisConfig.java              # MyBatis配置
│   │   ├── JacksonConfig.java              # Jackson配置
│   │   └── ShutdownHook.java               # 优雅关闭钩子
│   ├── controller/
│   │   └── InfoController.java             # 应用信息控制器
│   ├── mapper/
│   │   └── TaskMapper.java                 # 任务数据访问接口
│   ├── model/
│   │   └── TaskInfo.java                   # 任务信息实体
│   └── service/
│       ├── TaskMonitorService.java         # 任务监控服务
│       ├── TimeoutTasksService.java        # 超时任务服务
│       └── WeComAlertService.java          # 企业微信告警服务
├── src/main/resources/
│   ├── application.yml                     # 应用配置文件
│   └── mapper/
│       └── TaskMapper.xml                  # MyBatis映射文件
├── pom.xml                                 # Maven配置
├── Dockerfile.java                         # Java版本Dockerfile
├── k8s/                                    # Kubernetes部署文件
│   ├── deployment-java.yaml
│   ├── service-java.yaml
│   └── pvc.yaml
└── README.md                               # 项目说明
``` 