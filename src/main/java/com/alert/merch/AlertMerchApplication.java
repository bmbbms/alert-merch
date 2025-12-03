package com.alert.merch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商户入网审核流程监控系统主启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.alert.merch.mapper")
public class AlertMerchApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertMerchApplication.class, args);
    }
}
