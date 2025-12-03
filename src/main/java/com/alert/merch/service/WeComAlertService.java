package com.alert.merch.service;

import com.alert.merch.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信告警服务类
 */
@Slf4j
@Service
public class WeComAlertService {
    
    @Autowired
    private AppConfig appConfig;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 发送Markdown格式的告警消息
     */
    public void sendMarkdownAlert(String content, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.warn("企业微信Webhook地址为空，跳过发送告警");
            return;
        }
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json");
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("msgtype", "markdown");
            
            Map<String, String> markdown = new HashMap<>();
            markdown.put("content", content);
            payload.put("markdown", markdown);
            
            String jsonPayload = objectMapper.writeValueAsString(payload);
            httpPost.setEntity(new StringEntity(jsonPayload, "UTF-8"));
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);
                
                if (response.getStatusLine().getStatusCode() == 200) {
                    log.info("告警已发送成功");
                } else {
                    log.error("企业微信返回状态异常: {}, 响应: {}", 
                        response.getStatusLine().getStatusCode(), responseBody);
                }
            }
        } catch (IOException e) {
            log.error("发送告警失败", e);
        }
    }
    
    /**
     * 发送超时未领取告警
     */
    public void sendUnclaimedTimeoutAlert(int newAlertCount, int totalCount, int dailyCount, String taskIds) {
        String content = String.format(
            "【超时提醒】超时未领取\n您有<font color=\"red\">%d</font>条新的商户入网审核流程超时未领取，" +
            "当前超时未领取审核流程总共 <font color=\"red\">%d</font> 条，" +
            "当天累计超时未领取审核流程共 <font color=\"red\">%d</font> 条，请尽快操作。流程清单：%s\n",
            newAlertCount, totalCount, dailyCount, taskIds
        );
        
        sendMarkdownAlert(content, appConfig.getWecom().getWebhook());
    }
    
    /**
     * 发送超时未完成告警
     */
    public void sendUnfinishedTimeoutAlert(int newAlertCount, int totalCount, int dailyCount, String taskIds) {
        String content = String.format(
            "【超时提醒】超时未完成\n您有<font color=\"red\">%d</font>条新的商户入网审核流程已领取但审核超时，" +
            "当前审核超时流程总共 <font color=\"red\">%d</font> 条," +
            "当天累计审核超时流程共 <font color=\"red\">%d</font> 条，请尽快操作。\n流程清单：%s\n",
            newAlertCount, totalCount, dailyCount, taskIds
        );
        
        sendMarkdownAlert(content, appConfig.getWecom().getWebhook2());
    }
    
    /**
     * 发送每日统计告警
     * 
     * @param date 日期
     * @param totalTimeout 超时未完成条数
     * @param totalTasks 总入网条数
     */
    public void sendDailyStatsAlert(String date, int totalTimeout, int totalTasks) {
        String content = String.format(
            "【每日统计】\n昨日（%s）统计：\n" +
            "- 总入网条数: <font color=\"blue\">%d</font> 条\n" +
            "- 超时未完成: <font color=\"red\">%d</font> 条",
            date, totalTasks, totalTimeout
        );
        
        sendMarkdownAlert(content, appConfig.getWecom().getWebhook3());
    }
    
    /**
     * 发送当天入网人数统计
     * 
     * @param todayCount 当天累计入网人数
     * @param newCount 新增入网人数（可选，如果为0则不显示）
     * @param time 统计时间
     */
    public void sendTodayTaskStatsAlert(int todayCount, int newCount, String time) {
        String content;
        if (newCount > 0) {
            content = String.format(
                "【入网人数统计】\n统计时间: %s\n" +
                "当天新增入网人数: <font color=\"green\">%d</font> 人\n" +
                "当天累计入网人数: <font color=\"blue\">%d</font> 人",
                time, newCount, todayCount
            );
        } else {
            content = String.format(
                "【入网人数统计】\n统计时间: %s\n" +
                "当天累计入网人数: <font color=\"blue\">%d</font> 人",
                time, todayCount
            );
        }
        
        sendMarkdownAlert(content, appConfig.getWecom().getWebhook3());
    }
}
