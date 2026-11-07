package com.example.springai.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.springai.model.OfflineAgentChatModel;

@Configuration
public class AgentConfig {

    @Bean
    @Profile("!live")
    ChatModel offlineAgentChatModel(ObservationRegistry observationRegistry) {
        return new OfflineAgentChatModel(observationRegistry);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService agentExecutor() {
        // 生产环境应按供应商限流和租户配额调整，而不是无限创建线程。
        return new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    ChatClient agentChatClient(ChatModel chatModel, ObservationRegistry observationRegistry) {
        return ChatClient.builder(chatModel, observationRegistry, null, null)
            .defaultAdvisors(advisors -> advisors.param(
                ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false))
            .build();
    }

}
