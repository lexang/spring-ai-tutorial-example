package com.example.springai.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * 为查询转换和扩展创建隔离的低温度 ChatClient Builder。
 */
public final class RagChatClientBuilders {

    private RagChatClientBuilders() {
    }

    public static ChatClient.Builder forTransformation(ChatClient.Builder source) {
        return source.clone()
                .defaultOptions(ChatOptions.builder().temperature(0.0));
    }
}
