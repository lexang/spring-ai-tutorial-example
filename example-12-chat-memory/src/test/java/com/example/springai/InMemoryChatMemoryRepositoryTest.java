package com.example.springai;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryChatMemoryRepositoryTest {

    @Test
    void saveAllCopiesCallerListAndFindReturnsACopy() {
        var repository = new InMemoryChatMemoryRepository();
        var messages = new ArrayList<Message>();
        messages.add(new UserMessage("第一条"));

        repository.saveAll("list-reference", messages);
        messages.add(new AssistantMessage("调用方后来追加的消息"));

        assertThat(repository.findByConversationId("list-reference"))
                .extracting(Message::getText)
                .containsExactly("第一条");

        var returnedMessages = repository.findByConversationId("list-reference");
        returnedMessages.clear();

        assertThat(repository.findByConversationId("list-reference")).hasSize(1);
    }
}
