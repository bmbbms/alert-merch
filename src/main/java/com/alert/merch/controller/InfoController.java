package com.alert.merch.controller;

import com.alert.merch.config.AppConfig;
import com.alert.merch.service.TaskMonitorService;
import com.alert.merch.service.TimeoutTasksService;
import com.alert.merch.util.OceanBaseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用信息控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class InfoController {
    
    @Autowired
    private AppConfig appConfig;
    
    @Autowired
    private TimeoutTasksService timeoutTasksService;
    
    @Autowired
    private TaskMonitorService taskMonitorService;
    
    @Autowired
    private OceanBaseUtil oceanBaseUtil;
    
    /**
     * 获取应用状态信息
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("application", "Alert Merch");
        status.put("version", "1.0.0");
        status.put("status", "Running");
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        // 任务统计
        Map<String, Object> taskStats = new HashMap<>();
        taskStats.put("timeoutTasks", timeoutTasksService.getTimeoutTasksCount());
        taskStats.put("timeoutFinishTasks", timeoutTasksService.getTimeoutFinishTasksCount());
        taskStats.put("todayTaskCount", taskMonitorService.getTodayTaskCount());
        taskStats.put("lastStatsTime", taskMonitorService.getLastStatsTime() != null ? 
            taskMonitorService.getLastStatsTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "N/A");
        status.put("taskStats", taskStats);
        
        // 配置信息
        Map<String, Object> config = new HashMap<>();
        config.put("taskTimeoutMinutes", appConfig.getTask().getTimeoutMinutes());
        config.put("checkIntervalSeconds", appConfig.getTask().getCheckIntervalSeconds());
        config.put("unfinishedTimeoutMinutes", appConfig.getTask().getUnfinishedTimeoutMinutes());
        status.put("config", config);
        
        // OceanBase信息
        Map<String, Object> oceanbase = new HashMap<>();
        oceanbase.put("connectionTest", oceanBaseUtil.testConnection());
        oceanbase.put("databaseInfo", oceanBaseUtil.getDatabaseInfo());
        status.put("oceanbase", oceanbase);
        
        return status;
    }
    
    /**
     * 获取当天入网人数统计
     */
    @GetMapping("/today-stats")
    public Map<String, Object> getTodayStats() {
        Map<String, Object> stats = new HashMap<>();
        
        int todayCount = taskMonitorService.getTodayTaskCount();
        LocalDateTime lastStatsTime = taskMonitorService.getLastStatsTime();
        
        stats.put("today", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        stats.put("todayTaskCount", todayCount);
        stats.put("lastStatsTime", lastStatsTime != null ? 
            lastStatsTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "N/A");
        stats.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        return stats;
    }
}
