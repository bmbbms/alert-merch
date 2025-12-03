package com.alert.merch.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * OceanBase工具类
 */
@Slf4j
@Component
public class OceanBaseUtil {
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * 获取OceanBase数据库信息
     */
    public Map<String, Object> getDatabaseInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            // 获取数据库版本
            info.put("version", getDatabaseVersion(connection));
            
            // 获取数据库名称
            info.put("database", getDatabaseName(connection));
            
            // 获取当前用户
            info.put("user", getCurrentUser(connection));
            
            // 获取连接信息
            info.put("connectionInfo", getConnectionInfo(connection));
            
        } catch (SQLException e) {
            log.error("获取数据库信息失败", e);
            info.put("error", e.getMessage());
        }
        
        return info;
    }
    
    /**
     * 获取数据库版本
     */
    private String getDatabaseVersion(Connection connection) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT VERSION() FROM DUAL");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            log.warn("无法获取数据库版本，尝试其他方式", e);
            // 尝试OceanBase特有的版本查询
            try (PreparedStatement stmt = connection.prepareStatement("SELECT OB_VERSION() FROM DUAL");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            } catch (SQLException e2) {
                log.warn("无法获取OceanBase版本", e2);
            }
        }
        return "Unknown";
    }
    
    /**
     * 获取数据库名称
     */
    private String getDatabaseName(Connection connection) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT DATABASE() FROM DUAL");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            log.warn("无法获取数据库名称", e);
        }
        return "Unknown";
    }
    
    /**
     * 获取当前用户
     */
    private String getCurrentUser(Connection connection) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT USER() FROM DUAL");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            log.warn("无法获取当前用户", e);
        }
        return "Unknown";
    }
    
    /**
     * 获取连接信息
     */
    private Map<String, Object> getConnectionInfo(Connection connection) {
        Map<String, Object> connectionInfo = new HashMap<>();
        
        try {
            connectionInfo.put("autoCommit", connection.getAutoCommit());
            connectionInfo.put("transactionIsolation", connection.getTransactionIsolation());
            connectionInfo.put("catalog", connection.getCatalog());
            connectionInfo.put("schema", connection.getSchema());
        } catch (SQLException e) {
            log.warn("无法获取连接信息", e);
        }
        
        return connectionInfo;
    }
    
    /**
     * 测试OceanBase连接
     */
    public boolean testConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (SQLException e) {
            log.error("OceanBase连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 执行OceanBase特定的查询
     */
    public boolean executeOceanBaseQuery(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            return stmt.execute();
        } catch (SQLException e) {
            log.error("执行OceanBase查询失败: {}", sql, e);
            return false;
        }
    }
}
