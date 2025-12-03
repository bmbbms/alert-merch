package com.alert.merch.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Prometheus指标服务类
 */
@Slf4j
@Service
public class MetricsService {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    private Counter taskTotalCounter;
    private Counter unclaimedTotalCounter;
    private Counter unfinishedTotalCounter;
    
    @PostConstruct
    public void init() {
        // 注册新增入网总数指标
        taskTotalCounter = Counter.builder("task_total")
                .description("新增入网总数")
                .register(meterRegistry);
        
        // 注册未领取总数指标
        unclaimedTotalCounter = Counter.builder("unclaimed_total")
                .description("未领取总数")
                .register(meterRegistry);
        
        // 注册未完成总数指标
        unfinishedTotalCounter = Counter.builder("unfinished_total")
                .description("未完成总数")
                .register(meterRegistry);
        
        log.info("Prometheus指标已注册: task_total, unclaimed_total, unfinished_total");
    }
    
    /**
     * 增加新增入网总数
     * 
     * @param count 增加的数量
     */
    public void incrementTaskTotal(double count) {
        taskTotalCounter.increment(count);
    }
    
    /**
     * 增加未领取总数
     * 
     * @param count 增加的数量
     */
    public void incrementUnclaimedTotal(double count) {
        unclaimedTotalCounter.increment(count);
    }
    
    /**
     * 增加未完成总数
     * 
     * @param count 增加的数量
     */
    public void incrementUnfinishedTotal(double count) {
        unfinishedTotalCounter.increment(count);
    }
    
    /**
     * 获取当前新增入网总数
     * 
     * @return 总数
     */
    public double getTaskTotal() {
        return taskTotalCounter.count();
    }
    
    /**
     * 获取当前未领取总数
     * 
     * @return 总数
     */
    public double getUnclaimedTotal() {
        return unclaimedTotalCounter.count();
    }
    
    /**
     * 获取当前未完成总数
     * 
     * @return 总数
     */
    public double getUnfinishedTotal() {
        return unfinishedTotalCounter.count();
    }
}

