#!/bin/bash

# 商户入网审核流程监控系统构建脚本

echo "开始构建商户入网审核流程监控系统..."

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

# 清理并编译
echo "清理并编译项目..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "构建成功！"
    echo "JAR文件位置: target/alert-merch-1.0.0.jar"
    echo ""
    echo "运行方式:"
    echo "java -jar target/alert-merch-1.0.0.jar"
    echo ""
    echo "Docker构建:"
    echo "docker build -f Dockerfile.java -t alert-merch-java ."
else
    echo "构建失败！"
    exit 1
fi
