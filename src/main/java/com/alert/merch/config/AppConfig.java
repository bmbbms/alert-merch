package com.alert.merch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    
    private Task task = new Task();
    private Wecom wecom = new Wecom();
    private Persist persist = new Persist();
    
    @Data
    public static class Task {
        private int timeoutMinutes = 3;
        private int checkIntervalSeconds = 60;
        private int unfinishedTimeoutMinutes = 10;
    }
    
    @Data
    public static class Wecom {
        private String webhook;
        private String webhook2;
        private String webhook3;
    }
    
    @Data
    public static class Persist {
        private String path = ".";
    }
}
