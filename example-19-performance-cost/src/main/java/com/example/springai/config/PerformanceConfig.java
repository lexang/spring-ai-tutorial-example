package com.example.springai.config;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.springai.model.UsageAwareOfflineChatModel;

/**
 * 组装离线演示和可选 live 模型。
 */
@Configuration
@EnableConfigurationProperties(CostPolicyProperties.class)
public class PerformanceConfig {

    @Bean
    @Profile("!live")
    ChatModel offlineChatModel(ObservationRegistry observationRegistry) {
        return new UsageAwareOfflineChatModel(observationRegistry);
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel, ObservationRegistry observationRegistry) {
        return ChatClient.builder(chatModel, observationRegistry, null, null)
            .defaultAdvisors(advisors -> advisors.param(
                ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false))
            .build();
    }

}
