package com.alert.merch.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务信息实体类
 */
@Data
public class TaskInfo {
    
    private String taskId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    private String type; // 超时类型：unclaimed（未领取）, unfinished（未完成）
    
    public TaskInfo() {}
    
    public TaskInfo(String taskId, LocalDateTime createTime, String type) {
        this.taskId = taskId;
        this.createTime = createTime;
        this.type = type;
    }
}
