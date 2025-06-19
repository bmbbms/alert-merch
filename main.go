package main

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	_ "github.com/godror/godror"
)

const (
	timeoutTasksFile       = "timeout_tasks.json"
	timeoutFinishTasksFile = "timeout_finish_tasks.json"
)

var (
	oracleDSN                = os.Getenv("ORACLE_DSN")
	wecomWebhookURL          = os.Getenv("WECOM_WEBHOOK")
	wecomWebhookURL2         = os.Getenv("WECOM_WEBHOOK2") // 第二个企微群
	wecomWebhookURL3         = os.Getenv("WECOM_WEBHOOK3") // 第三个企微群
	timeoutMinutes           = getEnvAsInt("TASK_TIMEOUT_MINUTES", 3)
	checkInterval            = getEnvAsInt("CHECK_INTERVAL_SECONDS", 60)
	unfinishedTimeoutMinutes = getEnvAsInt("UNFINISHED_TIMEOUT_MINUTES", 10)
	db                       *sql.DB
	healthPort               = getEnvAsInt("HEALTH_PORT", 8080)

	// 告警记录，key为任务ID，value为最后告警时间
	alertRecords = struct {
		sync.RWMutex
		records map[string]time.Time
	}{
		records: make(map[string]time.Time),
	}

	// 未完成告警记录
	unfinishedAlertRecords = struct {
		sync.RWMutex
		records map[string]time.Time
	}{
		records: make(map[string]time.Time),
	}

	checkDailyStatsDone    bool
	checkDailyStatsDoneDay int
	lastSaveTime           time.Time // 上次保存时间
)

// 超时任务记录

type TimeoutTasks struct {
	tasks map[string]TaskInfo
	sync.RWMutex
}

// SaveToFile 保存到文件
func (tt *TimeoutTasks) SaveToFile(filename string) error {
	tt.RLock()
	defer tt.RUnlock()

	data, err := json.MarshalIndent(tt.tasks, "", "  ")
	if err != nil {
		return fmt.Errorf("序列化数据失败: %v", err)
	}

	err = ioutil.WriteFile(filename, data, 0644)
	if err != nil {
		return fmt.Errorf("写入文件失败: %v", err)
	}

	log.Printf("已保存超时任务数据到文件: %s", filename)
	return nil
}

// LoadFromFile 从文件加载
func (tt *TimeoutTasks) LoadFromFile(filename string) error {
	tt.Lock()
	defer tt.Unlock()

	data, err := ioutil.ReadFile(filename)
	if err != nil {
		if os.IsNotExist(err) {
			log.Printf("文件不存在，使用空数据: %s", filename)
			tt.tasks = make(map[string]TaskInfo)
			return nil
		}
		return fmt.Errorf("读取文件失败: %v", err)
	}

	err = json.Unmarshal(data, &tt.tasks)
	if err != nil {
		return fmt.Errorf("反序列化数据失败: %v", err)
	}

	log.Printf("已从文件加载超时任务数据: %s，共 %d 条记录", filename, len(tt.tasks))
	return nil
}

var (
	// 超时任务记录
	timeoutTasks = TimeoutTasks{
		tasks: make(map[string]TaskInfo),
	}
	timeoutFinishTasks = TimeoutTasks{
		tasks: make(map[string]TaskInfo),
	}
)

// TaskInfo 任务信息
type TaskInfo struct {
	TaskID     string
	CreateTime time.Time
	Type       string // 超时类型：unclaimed（未领取）, unfinished（未完成）
}

func initDB() error {
	var err error
	db, err = sql.Open("godror", oracleDSN)
	if err != nil {
		return fmt.Errorf("数据库连接失败: %v", err)
	}

	// 设置连接池参数
	db.SetMaxOpenConns(10)                  // 最大连接数
	db.SetMaxIdleConns(5)                   // 最大空闲连接数
	db.SetConnMaxLifetime(time.Hour)        // 连接最大生命周期
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

// isWorkingHours 检查当前时间是否在工作时间内（9:00-21:00）
func isWorkingHours() bool {
	now := time.Now()
	hour := now.Hour()
	return hour >= 9 && hour < 21
}

// shouldAlert 检查是否应该发送告警
func shouldAlert(taskID string) bool {
	alertRecords.RLock()
	lastAlert, exists := alertRecords.records[taskID]
	alertRecords.RUnlock()

	if !exists {
		return true
	}

	// 如果上次告警时间在10分钟内，则不发送
	return time.Since(lastAlert) > 10*time.Minute
}

// shouldAlertUnfinished 检查是否应该发送未完成告警
func shouldAlertUnfinished(taskID string) bool {
	unfinishedAlertRecords.RLock()
	lastAlert, exists := unfinishedAlertRecords.records[taskID]
	unfinishedAlertRecords.RUnlock()

	if !exists {
		return true
	}

	// 如果上次告警时间在10分钟内，则不发送
	return time.Since(lastAlert) > 10*time.Minute
}

// updateAlertRecord 更新告警记录
func updateAlertRecord(taskID string) {
	alertRecords.Lock()
	alertRecords.records[taskID] = time.Now()
	alertRecords.Unlock()
}

// updateUnfinishedAlertRecord 更新未完成告警记录
func updateUnfinishedAlertRecord(taskID string) {
	unfinishedAlertRecords.Lock()
	unfinishedAlertRecords.records[taskID] = time.Now()
	unfinishedAlertRecords.Unlock()
}

// checkTasks 检查所有任务状态
func checkTasks(ctx context.Context) {

	log.Printf("开始查询任务:\n")
	query := `
		SELECT PROC_ID, CREATE_TIME, 
		       CASE WHEN ASSIGNEE IS NULL AND ASSIGNEE_ID IS NULL THEN 'unclaimed' ELSE 'unfinished' END as TASK_STATUS
		FROM FLOWABLE_USER.T_CURRENT_TASK
		WHERE 
			CREATE_TIME >= TRUNC(SYSDATE) - 6
			AND PROC_KEY = 'ACT_MERCH_ACCESS_REGISTER'
			AND TASK_KEY IN ('LICENSE_MASTER_NEW_CHECK_TASK','PERSON_MASTER_NEW_CHECK_TASK')
	`

	rows, err := db.QueryContext(ctx, query)
	if err != nil {
		log.Printf("查询任务失败: %v\n", err)
		return
	}
	defer rows.Close()

	var unclaimedTasks, unfinishedTasks []TaskInfo
	now := time.Now()
	unclaimedTimeoutDuration := time.Duration(timeoutMinutes) * time.Minute
	unfinishedTimeoutDuration := time.Duration(unfinishedTimeoutMinutes) * time.Minute

	for rows.Next() {
		var task TaskInfo
		var taskStatus string
		if err := rows.Scan(&task.TaskID, &task.CreateTime, &taskStatus); err != nil {
			log.Printf("结果解析失败: %v\n", err)
			continue
		}

		task.Type = taskStatus

		// 根据任务状态和时间判断是否超时
		if taskStatus == "unclaimed" && now.Sub(task.CreateTime) > unclaimedTimeoutDuration {
			// 记录超时未分配的任务
			timeoutTasks.Lock()
			timeoutTasks.tasks[task.TaskID] = task
			timeoutTasks.Unlock()
			unclaimedTasks = append(unclaimedTasks, task)
		} else if taskStatus == "unfinished" && now.Sub(task.CreateTime) > unfinishedTimeoutDuration {
			// 记录超时未完成的任务
			timeoutFinishTasks.Lock()
			timeoutFinishTasks.tasks[task.TaskID] = task
			timeoutFinishTasks.Unlock()
			unfinishedTasks = append(unfinishedTasks, task)
		}
	}

	// 处理未领取超时任务
	if len(unclaimedTasks) > 0 && isWorkingHours() {
		var alertTasks []TaskInfo
		for _, task := range unclaimedTasks {
			if shouldAlert(task.TaskID) {
				alertTasks = append(alertTasks, task)
				updateAlertRecord(task.TaskID)
			}
		}

		if len(alertTasks) > 0 {
			taskIDs := make([]string, 0, len(alertTasks))
			for _, t := range alertTasks {
				taskIDs = append(taskIDs, fmt.Sprintf("<font color=\"blue\">%s</font>", t.TaskID))
			}
			taskIDStr := strings.Join(taskIDs, "\n")
			msg := fmt.Sprintf("【超时提醒】超时未领取\n您有<font color=\"red\">%d</font>条新的商户入网审核流程超时未领取，当前超时未领取审核流程总共 <font color=\"red\">%d</font> 条，当天累计超时未领取审核流程共 <font color=\"red\">%d</font> 条，请尽快操作。流程清单：%s",
				len(alertTasks), len(unclaimedTasks), len(timeoutTasks.tasks), taskIDStr)
			sendWeComAlertMarkdown(msg, wecomWebhookURL)
		}
	}

	// 处理已领取但未完成超时任务
	if len(unfinishedTasks) > 0 && isWorkingHours() {
		var alertTasks []TaskInfo
		for _, task := range unfinishedTasks {
			if shouldAlertUnfinished(task.TaskID) {
				alertTasks = append(alertTasks, task)
				updateUnfinishedAlertRecord(task.TaskID)
			}
		}

		if len(alertTasks) > 0 {
			taskIDs := make([]string, 0, len(alertTasks))
			for _, t := range alertTasks {
				taskIDs = append(taskIDs, fmt.Sprintf("<font color=\"blue\">%s</font>", t.TaskID))
			}
			taskIDStr := strings.Join(taskIDs, "\n")

			msg := fmt.Sprintf("【超时提醒】超时未完成\n您有<font color=\"red\">%d</font>条新的商户入网审核流程已领取但审核超时，当前审核超时流程总共 <font color=\"red\">%d</font> 条,当天累计审核超时流程共 <font color=\"red\">%d</font> 条，请尽快操作。\n流程清单：%s",
				len(alertTasks), len(unfinishedTasks), len(timeoutFinishTasks.tasks), taskIDStr)
			sendWeComAlertMarkdown(msg, wecomWebhookURL2)
		}
	}
}

func checkAndAlert() {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	// 检查每日统计（仅在9点执行）
	checkDailyStats(ctx)

	// 检查所有任务状态
	checkTasks(ctx)

	// 定期保存数据
	now := time.Now()
	if now.Sub(lastSaveTime) > 10*time.Minute {
		saveAllTimeoutTasks()
		lastSaveTime = now
	}
}

// checkDailyStats 检查每日统计
func checkDailyStats(ctx context.Context) {
	now := time.Now()
	currentDay := now.YearDay()

	// 每天0点重置标志
	if checkDailyStatsDoneDay != currentDay {
		checkDailyStatsDone = false
		checkDailyStatsDoneDay = currentDay
	}

	if now.Hour() == 9 && now.Minute() < 5 && !checkDailyStatsDone {
		// 统计未完成任务数量
		totalTimeout := 0
		log.Printf("开始每日统计，当前时间: %s", now.Format("2006-01-02 15:04:05"))
		timeoutFinishTasks.RLock()
		for _, task := range timeoutFinishTasks.tasks {
			if task.CreateTime.Before(now) && task.CreateTime.After(now.Add(-24*time.Hour)) {
				if task.CreateTime.Hour() < 21 && task.CreateTime.Hour() > 8 {
					totalTimeout++
				}
			}
		}
		timeoutFinishTasks.RUnlock()

		msg := fmt.Sprintf("【每日统计】\n昨日（%s）共有 <font color=\"red\">%d</font> 条商户入网审核流程超时未完成。",
			now.Add(-24*time.Hour).Format("2006-01-02"), totalTimeout)
		sendWeComAlertMarkdown(msg, wecomWebhookURL3)

		cleanupTimeoutTasks()
		checkDailyStatsDone = true
		log.Printf("每日统计已完成，今日不会重复执行")
	}
}

// cleanupTimeoutTasks 清理超时任务列表
func cleanupTimeoutTasks() {
	timeoutTasks.Lock()
	timeoutTasks.tasks = make(map[string]TaskInfo)
	timeoutTasks.Unlock()

	timeoutFinishTasks.Lock()
	timeoutFinishTasks.tasks = make(map[string]TaskInfo)
	timeoutFinishTasks.Unlock()

	log.Printf("已清空所有超时任务记录，开始新一天的统计")
}

// saveAllTimeoutTasks 保存所有超时任务数据
func saveAllTimeoutTasks() {
	log.Println("正在保存超时任务数据...")

	// 确保目录存在
	basePath := os.Getenv("PERSIST_PATH")
	if basePath != "" {
		if err := os.MkdirAll(basePath, 0755); err != nil {
			log.Printf("创建持久化目录失败: %v", err)
		}
	}

	if err := timeoutTasks.SaveToFile(getPersistPath(timeoutTasksFile)); err != nil {
		log.Printf("保存timeoutTasks失败: %v", err)
	}

	if err := timeoutFinishTasks.SaveToFile(getPersistPath(timeoutFinishTasksFile)); err != nil {
		log.Printf("保存timeoutFinishTasks失败: %v", err)
	}

	log.Println("超时任务数据保存完成")
}

// loadAllTimeoutTasks 加载所有超时任务数据
func loadAllTimeoutTasks() {
	log.Println("正在加载超时任务数据...")

	if err := timeoutTasks.LoadFromFile(getPersistPath(timeoutTasksFile)); err != nil {
		log.Printf("加载timeoutTasks失败: %v", err)
	}

	if err := timeoutFinishTasks.LoadFromFile(getPersistPath(timeoutFinishTasksFile)); err != nil {
		log.Printf("加载timeoutFinishTasks失败: %v", err)
	}

	log.Println("超时任务数据加载完成")
}

func sendWeComAlertMarkdown(content string, webhookURL string) {
	payload := map[string]interface{}{
		"msgtype": "markdown",
		"markdown": map[string]string{
			"content": content,
		},
	}
	data, _ := json.Marshal(payload)

	resp, err := http.Post(webhookURL, "application/json", bytes.NewBuffer(data))
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

// 获取持久化文件路径
func getPersistPath(filename string) string {
	basePath := os.Getenv("PERSIST_PATH")
	if basePath == "" {
		basePath = "." // 默认当前目录
	}
	return fmt.Sprintf("%s/%s", basePath, filename)
}

func main() {
	log.Println("启动定时任务监控程序...")

	// 加载持久化的超时任务数据
	loadAllTimeoutTasks()

	// 初始化上次保存时间
	lastSaveTime = time.Now()

	// 初始化数据库连接
	if err := initDB(); err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()

	// 启动健康检查服务器
	go startHealthServer()

	// 设置信号处理，用于优雅关闭
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	ticker := time.NewTicker(time.Duration(checkInterval) * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			checkAndAlert()
		case sig := <-sigChan:
			log.Printf("收到信号 %v，正在优雅关闭...", sig)
			// 保存数据
			saveAllTimeoutTasks()
			log.Println("程序已安全退出")
			return
		}
	}
}
