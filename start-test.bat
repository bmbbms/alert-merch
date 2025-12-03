@echo off
echo 启动Alert Merch应用测试...

REM 设置测试环境变量
set OCEANBASE_JDBC_URL=jdbc:h2:mem:testdb
set OCEANBASE_USERNAME=sa
set OCEANBASE_PASSWORD=
set HEALTH_PORT=8080

REM 启动应用
java -jar target/alert-merch-1.0.0.jar

pause


