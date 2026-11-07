package com.example.springai.config;

import com.example.springai.model.OfflineContentChatModel;
import com.example.springai.model.OfflineImageModel;
import com.example.springai.model.OfflineSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentStudioConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "offline", matchIfMissing = true)
    public ChatModel offlineContentChatModel() {
        return new OfflineContentChatModel();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "offline", matchIfMissing = true)
    public ImageModel offlineImageModel() {
        return new OfflineImageModel();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "offline", matchIfMissing = true)
    public TextToSpeechModel offlineSpeechModel() {
        return new OfflineSpeechModel();
    }

}
