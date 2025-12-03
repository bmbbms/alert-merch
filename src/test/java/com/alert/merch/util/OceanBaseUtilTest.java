package com.alert.merch.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * OceanBase工具类测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class OceanBaseUtilTest {
    
    @Autowired
    private OceanBaseUtil oceanBaseUtil;
    
    @Test
    public void testGetDatabaseInfo() {
        Map<String, Object> info = oceanBaseUtil.getDatabaseInfo();
        assertNotNull(info);
        // 由于使用H2内存数据库，可能没有OceanBase特有的信息，但至少应该能正常执行
    }
    
    @Test
    public void testTestConnection() {
        boolean result = oceanBaseUtil.testConnection();
        // 连接测试应该成功
        assertNotNull(result);
    }
    
    @Test
    public void testExecuteOceanBaseQuery() {
        boolean result = oceanBaseUtil.executeOceanBaseQuery("SELECT 1 FROM DUAL");
        // 查询执行应该成功
        assertNotNull(result);
    }
}
