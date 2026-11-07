package com.example.springai;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.datasource.url=jdbc:h2:mem:chat-memory-jdbc;DB_CLOSE_DELAY=-1",
        "spring.ai.chat.memory.repository.jdbc.initialize-schema=always",
        "server.port=0"
})
class JdbcChatMemoryRepositoryTest {

    @Autowired
    private JdbcChatMemoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void messagesUseSequenceIdOrderAndExposeCreationTimestamp() {
        String conversationId = "jdbc-order";
        this.repository.deleteByConversationId(conversationId);
        this.repository.saveAll(conversationId, List.of(
                new UserMessage("第一条"),
                new AssistantMessage("第二条"),
                new UserMessage("第三条")));

        var messages = this.repository.findByConversationId(conversationId);
        var sequenceIds = this.jdbcTemplate.queryForList(
                "SELECT sequence_id FROM SPRING_AI_CHAT_MEMORY "
                        + "WHERE conversation_id = ? ORDER BY sequence_id",
                Long.class,
                conversationId);

        assertThat(messages)
                .extracting(message -> message.getText())
                .containsExactly("第一条", "第二条", "第三条");
        assertThat(sequenceIds).containsExactly(0L, 1L, 2L);
        assertThat(messages)
                .allSatisfy(message -> assertThat(
                        message.getMetadata().get(JdbcChatMemoryRepository.CONVERSATION_TS))
                        .isInstanceOf(Instant.class));
    }

    @Test
    void saveAllReplacesTheWholeConversation() {
        String conversationId = "jdbc-replace";
        this.repository.saveAll(conversationId, List.of(
                new UserMessage("旧消息"),
                new AssistantMessage("旧回答")));

        this.repository.saveAll(conversationId, List.of(new UserMessage("新消息")));

        assertThat(this.repository.findByConversationId(conversationId))
                .extracting(message -> message.getText())
                .containsExactly("新消息");
    }

    @Test
    void unsupportedToolMessagesAreSilentlyFiltered() {
        String conversationId = "jdbc-tools";
        AssistantMessage toolCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "weather", "{\"city\":\"北京\"}")))
                .build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", "weather", "晴天")))
                .build();

        this.repository.saveAll(conversationId, List.of(
                new UserMessage("北京天气如何？"),
                toolCall,
                toolResponse,
                new AssistantMessage("北京今天是晴天。")));

        assertThat(this.repository.findByConversationId(conversationId))
                .extracting(message -> message.getText())
                .containsExactly("北京天气如何？", "北京今天是晴天。");
    }
}
