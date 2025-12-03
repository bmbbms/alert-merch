#!/bin/bash

# 商户入网审核流程监控系统启动脚本

echo "启动商户入网审核流程监控系统..."

# 检查Java版本
echo "检查Java版本..."
java -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Java环境，请安装JDK 11或更高版本"
    exit 1
fi

# 检查Maven
echo "检查Maven..."
mvn -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Maven，请安装Maven"
    exit 1
fi

# 编译项目
echo "编译项目..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "编译失败！"
    exit 1
fi

# 启动应用
echo "启动应用..."
java -jar target/alert-merch-1.0.0.jar

echo ""
echo "应用启动完成！"
echo ""
echo "健康检查端点："
echo "  - 总体健康状态: http://localhost:8080/actuator/health"
echo "  - 存活检查: http://localhost:8080/actuator/health/liveness"
echo "  - 就绪检查: http://localhost:8080/actuator/health/readiness"
echo "  - 应用信息: http://localhost:8080/actuator/info"
echo "  - 应用指标: http://localhost:8080/actuator/metrics"
echo "  - 状态API: http://localhost:8080/api/status"
echo ""
echo "按 Ctrl+C 停止应用"
