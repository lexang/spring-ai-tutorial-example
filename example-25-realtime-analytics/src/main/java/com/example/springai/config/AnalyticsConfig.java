package com.example.springai.config;

import com.example.springai.model.DeterministicAnalyticsChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "offline", matchIfMissing = true)
    public ChatModel deterministicAnalyticsChatModel() {
        return new DeterministicAnalyticsChatModel();
    }

    @Bean
    public ChatClient analyticsChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("""
                你是数据分析规划助手。只能从给定指标、维度和图表类型中选择，
                不得生成或要求执行任意 SQL，不得虚构查询结果。
                """)
            .build();
    }

}
