package main

import (
    "bytes"
    "context"
    "database/sql"
    "encoding/json"
    "fmt"
    "log"
    "net/http"
    "os"
    "strconv"
    "strings"
    "time"

    _ "github.com/godror/godror"
)

var (
    oracleDSN       = os.Getenv("ORACLE_DSN")
    wecomWebhookURL = os.Getenv("WECOM_WEBHOOK")
    timeoutMinutes  = getEnvAsInt("TASK_TIMEOUT_MINUTES", 3)
    checkInterval   = getEnvAsInt("CHECK_INTERVAL_SECONDS", 60)
    db             *sql.DB
    healthPort     = getEnvAsInt("HEALTH_PORT", 8080)
)

func initDB() error {
    var err error
    db, err = sql.Open("godror", oracleDSN)
    if err != nil {
        return fmt.Errorf("数据库连接失败: %v", err)
    }

    // 设置连接池参数
    db.SetMaxOpenConns(10)                // 最大连接数
    db.SetMaxIdleConns(5)                 // 最大空闲连接数
    db.SetConnMaxLifetime(time.Hour)      // 连接最大生命周期
    db.SetConnMaxIdleTime(30 * time.Minute) // 空闲连接最大生命周期

    // 测试连接
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    if err := db.PingContext(ctx); err != nil {
        return fmt.Errorf("数据库连接测试失败: %v", err)
    }

    return nil
}

func startHealthServer() {
    http.HandleFunc("/health/live", handleLiveness)
    http.HandleFunc("/health/ready", handleReadiness)
    
    addr := fmt.Sprintf(":%d", healthPort)
    log.Printf("启动健康检查服务器，监听端口: %d\n", healthPort)
    
    if err := http.ListenAndServe(addr, nil); err != nil {
        log.Fatalf("健康检查服务器启动失败: %v", err)
    }
}

func handleLiveness(w http.ResponseWriter, r *http.Request) {
    w.WriteHeader(http.StatusOK)
    w.Write([]byte("ok"))
}

func handleReadiness(w http.ResponseWriter, r *http.Request) {
    // 检查数据库连接
    ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
    defer cancel()
    
    if err := db.PingContext(ctx); err != nil {
        w.WriteHeader(http.StatusServiceUnavailable)
        w.Write([]byte("数据库连接异常"))
        return
    }
    
    w.WriteHeader(http.StatusOK)
    w.Write([]byte("ok"))
}

func main() {
    log.Println("启动定时任务监控程序...")
    
    // 初始化数据库连接
    if err := initDB(); err != nil {
        log.Fatalf("数据库初始化失败: %v", err)
    }
    defer db.Close()

    // 启动健康检查服务器
    go startHealthServer()

    ticker := time.NewTicker(time.Duration(checkInterval) * time.Second)
    defer ticker.Stop()

    for {
        select {
        case <-ticker.C:
            checkAndAlert()
        }
    }
}

func checkAndAlert() {
    ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
    defer cancel()

    query := `
        SELECT CREATE_TIME 
        FROM FLOWABLE_USER.T_CURRENT_TASK
        WHERE 
            CREATE_TIME >= TRUNC(SYSDATE) - 6
            AND ASSIGNEE IS NULL
            AND ASSIGNEE_ID IS NULL
            AND PROC_KEY = 'ACT_MERCH_ACCESS_REGISTER'
            AND TASK_KEY IN ('LICENSE_MASTER_NEW_CHECK_TASK','PERSON_MASTER_NEW_CHECK_TASK')
    `

    rows, err := db.QueryContext(ctx, query)
    if err != nil {
        log.Printf("查询失败: %v\n", err)
        return
    }
    defer rows.Close()

    now := time.Now()
    totalTasks := 0
    timeoutTasks := 0
    timeoutDuration := time.Duration(timeoutMinutes) * time.Minute

    for rows.Next() {
        var createTime time.Time
        if err := rows.Scan(&createTime); err != nil {
            log.Printf("结果解析失败: %v\n", err)
            continue
        }

        totalTasks++
        if now.Sub(createTime) > timeoutDuration {
            timeoutTasks++
        }
    }

    if err := rows.Err(); err != nil {
        log.Printf("遍历结果失败: %v\n", err)
        return
    }

    if timeoutTasks > 0 {
        msg := fmt.Sprintf("您有 [%d] 条【商户入网审核 4.0】入网审核流程待处理，且有 [%d] 条超时未处理，请尽快操作。", totalTasks, timeoutTasks)
        sendWeComAlert(msg)
    } else {
        log.Println("当前无待处理任务。")
    }
}

func sendWeComAlert(content string) {
    payload := map[string]interface{}{
        "msgtype": "text",
        "text": map[string]string{
            "content": content,
        },
    }
    data, _ := json.Marshal(payload)

    resp, err := http.Post(wecomWebhookURL, "application/json", bytes.NewBuffer(data))
    if err != nil {
        log.Printf("发送告警失败: %v\n", err)
        return
    }
    defer resp.Body.Close()

    if resp.StatusCode != http.StatusOK {
        log.Printf("企业微信返回状态异常: %s\n", resp.Status)
    } else {
        log.Println("告警已发送。")
    }
}

func getEnvAsInt(key string, defaultVal int) int {
    if valStr := os.Getenv(key); valStr != "" {
        if val, err := strconv.Atoi(valStr); err == nil {
            return val
        }
    }
    return defaultVal
}
