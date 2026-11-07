package com.example.springai.config;

import com.example.springai.model.DeterministicCodeChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CodeEngineeringConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "offline", matchIfMissing = true)
    public ChatModel deterministicCodeChatModel() {
        return new DeterministicCodeChatModel();
    }

}
