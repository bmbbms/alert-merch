package com.alert.merch.config;

import com.alert.merch.service.TimeoutTasksService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * 优雅关闭钩子
 */
@Slf4j
@Component
public class ShutdownHook {
    
    @Autowired
    private TimeoutTasksService timeoutTasksService;
    
    /**
     * 应用关闭前保存数据
     */
    @PreDestroy
    public void onShutdown() {
        log.info("收到关闭信号，正在优雅关闭...");
        timeoutTasksService.saveAllTimeoutTasks();
        log.info("程序已安全退出");
    }
}
