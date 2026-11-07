package com.example.springai;

import com.example.springai.service.ManualChatMemoryService;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

class ManualChatMemoryServiceTest {

    @Test
    void directChatModelFlowAddsUserAndAssistantMessages() {
        ChatModel model = prompt -> new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        prompt.getInstructions().size() > 1 ? "已看到历史" : "已记录"))));
        var memory = MessageWindowChatMemory.builder().build();
        var service = new ManualChatMemoryService(model, memory);

        assertThat(service.chat("manual-test", "第一轮")).isEqualTo("已记录");
        assertThat(service.chat("manual-test", "第二轮")).isEqualTo("已看到历史");
        assertThat(memory.get("manual-test"))
                .extracting(message -> message.getText())
                .containsExactly("第一轮", "已记录", "第二轮", "已看到历史");
    }
}
