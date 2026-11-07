package com.example.springai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 离线预览只创建接口占位模型，不允许生成端点访问远程服务。 */
@Configuration
@Profile("preview")
public class PreviewModelConfiguration {

    @Bean
    ChatModel previewChatModel() {
        return prompt -> {
            throw new IllegalStateException("预览模式不调用模型，请关闭 preview profile 并配置真实服务");
        };
    }
}
