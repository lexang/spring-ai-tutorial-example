package com.example.springai;

import java.util.List;

import com.example.springai.service.ChatConversationService;
import com.example.springai.service.MemoryManagementService;
import com.example.springai.support.ChatOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatConversationBoundaryTests {
    @Test void blankModelReplyDoesNotBecomeASuccessfulEmptyResponse() {
        var model = new RecordingModel("");
        var service = service(model);
        assertThatThrownBy(() -> service.chat("in-memory", "empty-response", "问题"))
                .isInstanceOf(ChatOperationException.class);
    }

    @Test void differentConversationIdsDoNotReuseAnotherConversationPrompt() {
        var model = new RecordingModel("回答");
        var service = service(model);
        service.chat("in-memory", "conversation-a", "仅属于 A 的消息");
        service.chat("in-memory", "conversation-b", "仅属于 B 的消息");
        assertThat(model.lastPrompt.getInstructions()).extracting(message -> message.getText())
                .contains("仅属于 B 的消息").doesNotContain("仅属于 A 的消息");
    }

    private static ChatConversationService service(ChatModel model) {
        var factory = new StaticListableBeanFactory();
        factory.addBean("chatModel", model);
        var inMemory = MessageWindowChatMemory.builder().maxMessages(6).build();
        var jdbcSubstitute = MessageWindowChatMemory.builder().maxMessages(20).build();
        return new ChatConversationService(factory.getBeanProvider(ChatModel.class), inMemory, jdbcSubstitute,
                new MemoryManagementService(inMemory, jdbcSubstitute));
    }

    static class RecordingModel implements ChatModel {
        final String answer;
        Prompt lastPrompt;
        RecordingModel(String answer) { this.answer = answer; }
        @Override public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }
    }
}
