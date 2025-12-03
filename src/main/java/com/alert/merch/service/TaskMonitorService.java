package com.alert.merch.service;

import com.alert.merch.config.AppConfig;
import com.alert.merch.model.TaskInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.alert.merch.mapper.TaskMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务监控服务类
 */
@Slf4j
@Service
public class TaskMonitorService {
    
    @Autowired
    private TaskMapper taskMapper;
    
    @Autowired
    private AppConfig appConfig;
    
    @Autowired
    private TimeoutTasksService timeoutTasksService;
    
    @Autowired
    private WeComAlertService weComAlertService;
    
    @Autowired
    private MetricsService metricsService;
    
    // 告警记录，key为任务ID，value为最后告警时间
    private final Map<String, LocalDateTime> alertRecords = new ConcurrentHashMap<>();
    
    // 未完成告警记录
    private final Map<String, LocalDateTime> unfinishedAlertRecords = new ConcurrentHashMap<>();
    
    // 每日统计相关
    private boolean checkDailyStatsDone = false;
    private int checkDailyStatsDoneDay = 0;
    private LocalDateTime lastSaveTime = LocalDateTime.now();
    
    // 当天入网人数统计相关
    private int todayTaskCount = 0;  // 当天累计入网人数（根据task_id累加）
    private Set<String> todayTaskIds = new HashSet<>();  // 已统计的task_id集合
    private int lastStatsDay = 0;
    private LocalDateTime lastStatsTime = LocalDateTime.now();
    
    // 每日入网人数统计（日期 -> 入网人数），用于保存历史统计
    private final Map<String, Integer> dailyTaskStats = new ConcurrentHashMap<>();
    
    // 已统计的未领取任务ID集合（用于Prometheus指标，避免重复统计）
    private final Set<String> countedUnclaimedTaskIds = new HashSet<>();
    
    // 已统计的未完成任务ID集合（用于Prometheus指标，避免重复统计）
    private final Set<String> countedUnfinishedTaskIds = new HashSet<>();
    
    /**
     * 定时检查任务状态
     */
    @Scheduled(fixedDelayString = "${app.task.check-interval-seconds}000")
    public void checkAndAlert() {
        log.info("开始查询任务...");
        
        try {
            // 检查每日统计（仅在9点执行）
            checkDailyStats();
            
            // 统计当天新增入网人数（与检查频率一致）
            statisticsTodayTasks();
            
            // 检查所有任务状态
            checkTasks();
            
            // 定期保存数据
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(lastSaveTime.plusMinutes(10))) {
                timeoutTasksService.saveAllTimeoutTasks();
                lastSaveTime = now;
            }
            
        } catch (Exception e) {
            log.error("任务检查异常", e);
        }
    }
    
    /**
     * 统计当天新增入网人数
     * 与任务检查频率一致，根据task_id进行累加统计
     * 因为任务完成后记录会被删除，需要根据task_id记录已统计的任务
     */
    private void statisticsTodayTasks() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int currentDay = now.getDayOfYear();
            
            // 如果是新的一天，先保存前一天的统计，然后重置
            if (lastStatsDay != currentDay && lastStatsDay != 0) {
                // 保存前一天的统计
                String yesterdayDate = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                dailyTaskStats.put(yesterdayDate, todayTaskCount);
                log.info("保存前一天（{}）的入网人数统计: {} 人", yesterdayDate, todayTaskCount);
            }
            
            // 如果是新的一天，重置统计
            if (lastStatsDay != currentDay) {
                todayTaskCount = 0;
                todayTaskIds.clear();
                lastStatsDay = currentDay;
                log.info("新的一天开始，重置当天入网人数统计");
            }
            
            // 查询当天任务
            List<String> taskKeys = Arrays.asList("LICENSE_MASTER_NEW_CHECK_TASK", "PERSON_MASTER_NEW_CHECK_TASK");
            List<TaskInfo> todayTasks = taskMapper.selectTodayTasks("ACT_MERCH_ACCESS_REGISTER", taskKeys);
            
            // 根据task_id进行累加统计
            int newTaskCount = 0;
            for (TaskInfo task : todayTasks) {
                String taskId = task.getTaskId();
                // 如果这个task_id还没有统计过，则累加
                if (!todayTaskIds.contains(taskId)) {
                    todayTaskIds.add(taskId);
                    todayTaskCount++;
                    newTaskCount++;
                }
            }
            
            // 记录统计结果
            if (newTaskCount > 0) {
                log.info("【入网人数统计】当前时间: {}, 新增入网人数: {} 人, 累计入网人数: {} 人", 
                    now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                    newTaskCount, todayTaskCount);
                lastStatsTime = now;
                
                // 更新Prometheus指标：新增入网总数
                metricsService.incrementTaskTotal(newTaskCount);
            } else {
                log.debug("【入网人数统计】当前时间: {}, 累计入网人数: {} 人（无新增）", 
                    now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                    todayTaskCount);
            }
            
        } catch (Exception e) {
            log.error("统计当天入网人数异常", e);
        }
    }
    
    /**
     * 检查任务状态
     */
    private void checkTasks() {
        // 查询最近6天的任务，使用更灵活的查询方法
        List<String> taskKeys = Arrays.asList("LICENSE_MASTER_NEW_CHECK_TASK", "PERSON_MASTER_NEW_CHECK_TASK");
        List<TaskInfo> allTasks = taskMapper.selectTasksByStatus(6, "ACT_MERCH_ACCESS_REGISTER", taskKeys);
        
        List<TaskInfo> unclaimedTasks = new ArrayList<>();
        List<TaskInfo> unfinishedTasks = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        int unclaimedTimeoutMinutes = appConfig.getTask().getTimeoutMinutes();
        int unfinishedTimeoutMinutes = appConfig.getTask().getUnfinishedTimeoutMinutes();
        
        log.info("当前时间: {}, 未领取超时时间: {}分钟, 未完成超时时间: {}分钟", 
            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
            unclaimedTimeoutMinutes, unfinishedTimeoutMinutes);
        
        for (TaskInfo task : allTasks) {
            log.info("检查任务: ID={}, 创建时间={}, 状态={}", 
                task.getTaskId(), task.getCreateTime(), task.getType());
            
            // 根据任务状态和时间判断是否超时
            if ("unclaimed".equals(task.getType()) && 
                now.isAfter(task.getCreateTime().plusMinutes(unclaimedTimeoutMinutes))) {
                log.info("发现超时未分配任务: ID={}, 超时时间={}分钟", 
                    task.getTaskId(), unclaimedTimeoutMinutes);
                // 记录超时未分配的任务
                timeoutTasksService.saveTimeoutTask(task);
                unclaimedTasks.add(task);
                
                // 更新Prometheus指标：未领取总数（只统计一次）
                String taskId = task.getTaskId();
                if (!countedUnclaimedTaskIds.contains(taskId)) {
                    countedUnclaimedTaskIds.add(taskId);
                    metricsService.incrementUnclaimedTotal(1);
                }
            } else if ("unfinished".equals(task.getType()) && 
                     now.isAfter(task.getCreateTime().plusMinutes(unfinishedTimeoutMinutes))) {
                log.info("发现超时未完成任务: ID={}, 超时时间={}分钟", 
                    task.getTaskId(), unfinishedTimeoutMinutes);
                // 记录超时未完成的任务
                timeoutTasksService.saveTimeoutFinishTask(task);
                unfinishedTasks.add(task);
                
                // 更新Prometheus指标：未完成总数（只统计一次）
                String taskId = task.getTaskId();
                if (!countedUnfinishedTaskIds.contains(taskId)) {
                    countedUnfinishedTaskIds.add(taskId);
                    metricsService.incrementUnfinishedTotal(1);
                }
            } else {
                log.info("任务未超时: ID={}, 状态={}", task.getTaskId(), task.getType());
            }
        }
        
        // 处理未领取超时任务
        if (!unclaimedTasks.isEmpty() && isWorkingHours()) {
            List<TaskInfo> alertTasks = unclaimedTasks.stream()
                .filter(task -> shouldAlert(task.getTaskId()))
                .collect(Collectors.toList());
            
            if (!alertTasks.isEmpty()) {
                String taskIds = alertTasks.stream()
                    .map(task -> String.format("<font color=\"blue\">%s</font>", task.getTaskId()))
                    .collect(Collectors.joining("\n"));
                
                weComAlertService.sendUnclaimedTimeoutAlert(
                    alertTasks.size(),
                    unclaimedTasks.size(),
                    timeoutTasksService.getTimeoutTasksCount(),
                    taskIds
                );
                
                // 更新告警记录
                alertTasks.forEach(task -> updateAlertRecord(task.getTaskId()));
            }
        }
        
        // 处理已领取但未完成超时任务
        if (!unfinishedTasks.isEmpty()) {
            log.info("发现{}个超时未完成任务", unfinishedTasks.size());
            if (isWorkingHours()) {
                log.info("当前在工作时间内，准备发送告警");
                List<TaskInfo> alertTasks = unfinishedTasks.stream()
                    .filter(task -> shouldAlertUnfinished(task.getTaskId()))
                    .collect(Collectors.toList());
                
                if (!alertTasks.isEmpty()) {
                    String taskIds = alertTasks.stream()
                        .map(task -> String.format("<font color=\"blue\">%s</font>", task.getTaskId()))
                        .collect(Collectors.joining("\n"));
                    
                    weComAlertService.sendUnfinishedTimeoutAlert(
                        alertTasks.size(),
                        unfinishedTasks.size(),
                        timeoutTasksService.getTimeoutFinishTasksCount(),
                        taskIds
                    );
                    
                    // 更新告警记录
                    alertTasks.forEach(task -> updateUnfinishedAlertRecord(task.getTaskId()));
                }
            } else {
                log.info("当前不在工作时间内，跳过告警发送");
            }
        }
    }
    
    /**
     * 检查每日统计
     */
    private void checkDailyStats() {
        LocalDateTime now = LocalDateTime.now();
        int currentDay = now.getDayOfYear();
        
        // 每天0点重置标志
        if (checkDailyStatsDoneDay != currentDay) {
            checkDailyStatsDone = false;
            checkDailyStatsDoneDay = currentDay;
        }
        
        if (now.getHour() == 9 && now.getMinute() < 5 && !checkDailyStatsDone) {
            log.info("开始每日统计，当前时间: {}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            // 统计未完成任务数量
            int totalTimeout = 0;
            Map<String, TaskInfo> timeoutFinishTasks = timeoutTasksService.getTimeoutFinishTasks();
            
            for (TaskInfo task : timeoutFinishTasks.values()) {
                LocalDateTime createTime = task.getCreateTime();
                if (createTime.isBefore(now) && createTime.isAfter(now.minusDays(1))) {
                    if (createTime.getHour() < 21 && createTime.getHour() > 8) {
                        totalTimeout++;
                    }
                }
            }
            
            String yesterday = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // 获取昨日总入网条数（从保存的统计中读取）
            int yesterdayTotalTasks = dailyTaskStats.getOrDefault(yesterday, 0);
            
            // 如果统计中没有昨天的数据，可能是跨天时统计还没保存，尝试保存当前统计（如果适用）
            if (yesterdayTotalTasks == 0) {
                // 如果当前统计的是昨天（系统可能在跨天时重启了），保存昨天的统计
                if (lastStatsDay != currentDay && todayTaskCount > 0) {
                    dailyTaskStats.put(yesterday, todayTaskCount);
                    yesterdayTotalTasks = todayTaskCount;
                    log.info("检测到昨天的统计未保存，已保存昨天（{}）的入网人数统计: {} 人", yesterday, todayTaskCount);
                } else {
                    log.warn("未找到昨天（{}）的入网人数统计，使用0", yesterday);
                }
            }
            
            weComAlertService.sendDailyStatsAlert(yesterday, totalTimeout, yesterdayTotalTasks);
            
            timeoutTasksService.cleanupAllTimeoutTasks();
            checkDailyStatsDone = true;
            log.info("每日统计已完成，今日不会重复执行");
        }
    }
    
    /**
     * 检查当前时间是否在工作时间内（9:00-21:00）
     */
    private boolean isWorkingHours() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        return hour >= 9 && hour < 21;
    }
    
    /**
     * 检查是否应该发送告警
     */
    private boolean shouldAlert(String taskId) {
        LocalDateTime lastAlert = alertRecords.get(taskId);
        if (lastAlert == null) {
            return true;
        }
        
        // 如果上次告警时间在10分钟内，则不发送
        return LocalDateTime.now().isAfter(lastAlert.plusMinutes(10));
    }
    
    /**
     * 检查是否应该发送未完成告警
     */
    private boolean shouldAlertUnfinished(String taskId) {
        LocalDateTime lastAlert = unfinishedAlertRecords.get(taskId);
        if (lastAlert == null) {
            return true;
        }
        
        // 如果上次告警时间在10分钟内，则不发送
        return LocalDateTime.now().isAfter(lastAlert.plusMinutes(10));
    }
    
    /**
     * 更新告警记录
     */
    private void updateAlertRecord(String taskId) {
        alertRecords.put(taskId, LocalDateTime.now());
    }
    
    /**
     * 更新未完成告警记录
     */
    private void updateUnfinishedAlertRecord(String taskId) {
        unfinishedAlertRecords.put(taskId, LocalDateTime.now());
    }
    
    /**
     * 获取当天入网人数统计
     * 
     * @return 当天入网人数
     */
    public int getTodayTaskCount() {
        return todayTaskCount;
    }
    
    /**
     * 获取最后统计时间
     * 
     * @return 最后统计时间
     */
    public LocalDateTime getLastStatsTime() {
        return lastStatsTime;
    }
}
