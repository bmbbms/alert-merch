package com.alert.merch.config;

import com.alert.merch.model.TaskInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jackson配置测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
class JacksonConfigTest {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testLocalDateTimeSerialization() throws Exception {
        // 创建测试数据
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskId("test-task-001");
        taskInfo.setCreateTime(LocalDateTime.of(2025, 8, 21, 17, 30, 0));
        taskInfo.setType("unclaimed");
        
        // 序列化
        String json = objectMapper.writeValueAsString(taskInfo);
        assertNotNull(json);
        assertTrue(json.contains("2025-08-21 17:30:00"));
        
        // 反序列化
        TaskInfo deserializedTask = objectMapper.readValue(json, TaskInfo.class);
        assertNotNull(deserializedTask);
        assertEquals("test-task-001", deserializedTask.getTaskId());
        assertEquals(LocalDateTime.of(2025, 8, 21, 17, 30, 0), deserializedTask.getCreateTime());
        assertEquals("unclaimed", deserializedTask.getType());
    }
}
