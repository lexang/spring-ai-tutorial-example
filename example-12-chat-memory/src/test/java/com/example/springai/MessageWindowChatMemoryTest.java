package com.example.springai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class MessageWindowChatMemoryTest {

    @Test
    void evictionSnapsToCompleteTurnBoundary() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(3)
                .build();

        memory.add("turn-boundary", List.of(
                new UserMessage("问题 1"),
                new AssistantMessage("回答 1"),
                new UserMessage("问题 2"),
                new AssistantMessage("回答 2")));

        assertThat(memory.get("turn-boundary")).containsExactly(
                new UserMessage("问题 2"),
                new AssistantMessage("回答 2"));
    }

    @Test
    void systemMessageIsPreservedAndNewSystemMessageReplacesOldOnes() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(3)
                .build();

        memory.add("system-message", List.of(
                new SystemMessage("旧规则"),
                new UserMessage("问题 1"),
                new AssistantMessage("回答 1")));
        memory.add("system-message", List.of(
                new UserMessage("问题 2"),
                new AssistantMessage("回答 2")));

        assertThat(memory.get("system-message")).containsExactly(
                new SystemMessage("旧规则"),
                new UserMessage("问题 2"),
                new AssistantMessage("回答 2"));

        memory.add("system-message", new SystemMessage("新规则"));

        assertThat(memory.get("system-message"))
                .contains(new SystemMessage("新规则"))
                .doesNotContain(new SystemMessage("旧规则"));
    }

    @Test
    void repeatedEqualSystemMessagesCanExceedConfiguredMaximum() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(1)
                .build();

        memory.add("repeated-system-message", new SystemMessage("同一规则"));
        memory.add("repeated-system-message", new SystemMessage("同一规则"));

        assertThat(memory.get("repeated-system-message")).containsExactly(
                new SystemMessage("同一规则"),
                new SystemMessage("同一规则"));
    }

    @Test
    void conversationsAreIsolatedAndCanBeClearedIndependently() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();

        memory.add("conversation-a", new UserMessage("苹果"));
        memory.add("conversation-b", new UserMessage("香蕉"));
        memory.clear("conversation-a");

        assertThat(memory.get("conversation-a")).isEmpty();
        assertThat(memory.get("conversation-b"))
                .containsExactly(new UserMessage("香蕉"));
    }
}
