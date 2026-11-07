package com.example.springai;

import com.example.springai.service.ChatConversationService;
import com.example.springai.service.VectorMemoryChatService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式设置 RUN_CHAT_MEMORY_INTEGRATION_TESTS=true 时才调用真实模型并加载 ONNX。
 */
@EnabledIfEnvironmentVariable(
        named = "RUN_CHAT_MEMORY_INTEGRATION_TESTS",
        matches = "(?i)true")
@SpringBootTest(properties = {
        "spring.ai.model.chat=anthropic",
        "spring.ai.model.embedding=transformers",
        "spring.datasource.url=jdbc:h2:mem:chat-memory-integration;DB_CLOSE_DELAY=-1",
        "spring.ai.chat.memory.repository.jdbc.initialize-schema=always",
        "server.port=0"
})
class ConfiguredModelsIntegrationTest {

    @Autowired
    private ChatConversationService chatConversationService;

    @Autowired
    private VectorMemoryChatService vectorMemoryChatService;

    @Test
    void realChatAndVectorMemoryCanBeEnabled() {
        assertThat(this.chatConversationService.status().enabled()).isTrue();
        assertThat(this.vectorMemoryChatService.status().enabled()).isTrue();

        var chatReply = this.chatConversationService.chat(
                "in-memory", "integration-chat", "请只回复：模型已连接");
        var vectorReply = this.vectorMemoryChatService.chat(
                "integration-vector", "请只回复：向量记忆已连接", 3);

        assertThat(chatReply.response()).isNotBlank();
        assertThat(vectorReply.response()).isNotBlank();
    }
}
