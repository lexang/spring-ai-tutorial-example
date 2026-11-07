package com.example.springai;

import com.example.springai.service.ChatConversationService;
import com.example.springai.service.MemoryManagementService;
import com.example.springai.service.VectorMemoryChatService;
import com.example.springai.support.FeatureUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.datasource.url=jdbc:h2:mem:chat-memory-app;DB_CLOSE_DELAY=-1",
        "spring.ai.chat.memory.repository.jdbc.initialize-schema=always",
        "server.port=0"
})
class ChatMemoryApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ChatConversationService chatConversationService;

    @Autowired
    private VectorMemoryChatService vectorMemoryChatService;

    @Autowired
    private MemoryManagementService memoryManagementService;

    @Test
    void applicationStartsOfflineWithoutModelBeans() {
        assertThat(this.context.getBeansOfType(ChatModel.class)).isEmpty();
        assertThat(this.context.getBeansOfType(EmbeddingModel.class)).isEmpty();
        assertThat(this.chatConversationService.status().enabled()).isFalse();
        assertThat(this.vectorMemoryChatService.status().enabled()).isFalse();
    }

    @Test
    void memoryManagementWorksOfflineForBothStores() {
        String inMemoryId = "offline-memory";
        String jdbcId = "offline-jdbc";
        this.memoryManagementService.clear("in-memory", inMemoryId);
        this.memoryManagementService.clear("jdbc", jdbcId);

        var inMemory = this.memoryManagementService.addMessage(
                "in-memory", inMemoryId, "user", "离线内存消息");
        var jdbc = this.memoryManagementService.addMessage(
                "jdbc", jdbcId, "assistant", "离线 JDBC 消息");

        assertThat(inMemory.messageCount()).isEqualTo(1);
        assertThat(inMemory.messages().get(0).createdAt()).isNull();
        assertThat(jdbc.messageCount()).isEqualTo(1);
        assertThat(jdbc.messages().get(0).createdAt()).isNotNull();
    }

    @Test
    void chatEndpointServiceReportsHowToEnableModel() {
        assertThatThrownBy(() -> this.chatConversationService.chat(
                "in-memory", "offline-chat", "你好"))
                .isInstanceOf(FeatureUnavailableException.class)
                .hasMessageContaining("SPRING_AI_CHAT_MODEL");
    }
}
