package com.alert.merch.mapper;

import com.alert.merch.model.TaskInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TaskMapper测试类
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class TaskMapperTest {
    
    @Autowired
    private TaskMapper taskMapper;
    
    @Test
    public void testSelectTimeoutTasks() {
        List<TaskInfo> tasks = taskMapper.selectTimeoutTasks(6);
        assertNotNull(tasks);
        // 由于使用H2内存数据库，可能没有数据，但至少应该能正常执行
    }
    
    @Test
    public void testSelectTasksByStatus() {
        List<String> taskKeys = Arrays.asList("LICENSE_MASTER_NEW_CHECK_TASK", "PERSON_MASTER_NEW_CHECK_TASK");
        List<TaskInfo> tasks = taskMapper.selectTasksByStatus(6, "ACT_MERCH_ACCESS_REGISTER", taskKeys);
        assertNotNull(tasks);
        // 由于使用H2内存数据库，可能没有数据，但至少应该能正常执行
    }
}
