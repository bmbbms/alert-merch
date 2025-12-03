package com.alert.merch.service;

import com.alert.merch.config.AppConfig;
import com.alert.merch.model.TaskInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 超时任务服务类
 */
@Slf4j
@Service
public class TimeoutTasksService {
    
    private static final String TIMEOUT_TASKS_FILE = "timeout_tasks.json";
    private static final String TIMEOUT_FINISH_TASKS_FILE = "timeout_finish_tasks.json";
    
    @Autowired
    private AppConfig appConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 超时未领取任务
    private final Map<String, TaskInfo> timeoutTasks = new ConcurrentHashMap<>();
    
    // 超时未完成任务
    private final Map<String, TaskInfo> timeoutFinishTasks = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        loadAllTimeoutTasks();
    }
    
    /**
     * 保存超时未领取任务
     */
    public void saveTimeoutTask(TaskInfo task) {
        timeoutTasks.put(task.getTaskId(), task);
    }
    
    /**
     * 保存超时未完成任务
     */
    public void saveTimeoutFinishTask(TaskInfo task) {
        timeoutFinishTasks.put(task.getTaskId(), task);
    }
    
    /**
     * 获取超时未领取任务数量
     */
    public int getTimeoutTasksCount() {
        return timeoutTasks.size();
    }
    
    /**
     * 获取超时未完成任务数量
     */
    public int getTimeoutFinishTasksCount() {
        return timeoutFinishTasks.size();
    }
    
    /**
     * 获取超时未领取任务列表
     */
    public Map<String, TaskInfo> getTimeoutTasks() {
        return new ConcurrentHashMap<>(timeoutTasks);
    }
    
    /**
     * 获取超时未完成任务列表
     */
    public Map<String, TaskInfo> getTimeoutFinishTasks() {
        return new ConcurrentHashMap<>(timeoutFinishTasks);
    }
    
    /**
     * 清理所有超时任务
     */
    public void cleanupAllTimeoutTasks() {
        timeoutTasks.clear();
        timeoutFinishTasks.clear();
        log.info("已清空所有超时任务记录，开始新一天的统计");
    }
    
    /**
     * 保存所有超时任务数据到文件
     */
    public void saveAllTimeoutTasks() {
        log.info("正在保存超时任务数据...");
        
        try {
            // 确保目录存在
            String basePath = appConfig.getPersist().getPath();
            Path persistPath = Paths.get(basePath);
            if (!Files.exists(persistPath)) {
                Files.createDirectories(persistPath);
            }
            
            // 保存超时未领取任务
            saveToFile(timeoutTasks, getPersistPath(TIMEOUT_TASKS_FILE));
            
            // 保存超时未完成任务
            saveToFile(timeoutFinishTasks, getPersistPath(TIMEOUT_FINISH_TASKS_FILE));
            
            log.info("超时任务数据保存完成");
        } catch (Exception e) {
            log.error("保存超时任务数据失败", e);
        }
    }
    
    /**
     * 从文件加载所有超时任务数据
     */
    public void loadAllTimeoutTasks() {
        log.info("正在加载超时任务数据...");
        
        try {
            loadFromFile(timeoutTasks, getPersistPath(TIMEOUT_TASKS_FILE));
            loadFromFile(timeoutFinishTasks, getPersistPath(TIMEOUT_FINISH_TASKS_FILE));
            log.info("超时任务数据加载完成");
        } catch (Exception e) {
            log.error("加载超时任务数据失败", e);
        }
    }
    
    /**
     * 保存数据到文件
     */
    private void saveToFile(Map<String, TaskInfo> tasks, String filePath) throws IOException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        Files.write(Paths.get(filePath), json.getBytes());
        log.info("已保存超时任务数据到文件: {}", filePath);
    }
    
    /**
     * 从文件加载数据
     */
    private void loadFromFile(Map<String, TaskInfo> tasks, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            log.info("文件不存在，使用空数据: {}", filePath);
            tasks.clear();
            return;
        }
        
        String json = Files.readString(Paths.get(filePath));
        Map<String, TaskInfo> loadedTasks = objectMapper.readValue(json, 
            new TypeReference<Map<String, TaskInfo>>() {});
        
        tasks.clear();
        tasks.putAll(loadedTasks);
        
        log.info("已从文件加载超时任务数据: {}，共 {} 条记录", filePath, tasks.size());
    }
    
    /**
     * 获取持久化文件路径
     */
    private String getPersistPath(String filename) {
        String basePath = appConfig.getPersist().getPath();
        return Paths.get(basePath, filename).toString();
    }
}
