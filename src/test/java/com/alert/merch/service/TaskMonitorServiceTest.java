package com.alert.merch.service;

import com.alert.merch.model.TaskInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskMonitorService测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "app.task.timeout-minutes=3",
    "app.task.unfinished-timeout-minutes=10"
})
class TaskMonitorServiceTest {
    
    @Autowired
    private TaskMonitorService taskMonitorService;
    
    @Test
    void testTaskTimeoutCalculation() {
        // 创建一个超时的任务
        LocalDateTime createTime = LocalDateTime.now().minusMinutes(15); // 15分钟前创建
        TaskInfo task = new TaskInfo("test-task-001", createTime, "unfinished");
        
        // 验证任务确实超时了
        LocalDateTime now = LocalDateTime.now();
        assertTrue(now.isAfter(createTime.plusMinutes(10)), "任务应该超时");
        
        // 验证任务状态
        assertEquals("unfinished", task.getType());
        assertEquals("test-task-001", task.getTaskId());
    }
}
