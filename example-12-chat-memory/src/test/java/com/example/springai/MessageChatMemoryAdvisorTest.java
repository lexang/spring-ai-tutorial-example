package com.example.springai;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageChatMemoryAdvisorTest {

    @Test
    void advisorInjectsPreviousMessagesAndStoresFinalExchange() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(10).build();
        RecordingChatModel chatModel = new RecordingChatModel();
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();

        String conversationId = "advisor-test";
        String first = chatClient.prompt()
                .user("我叫张三")
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        String second = chatClient.prompt()
                .user("我叫什么名字？")
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        assertThat(first).isEqualTo("已记录");
        assertThat(second).isEqualTo("你叫张三");
        assertThat(memory.get(conversationId))
                .extracting(message -> message.getText())
                .containsExactly("我叫张三", "已记录", "我叫什么名字？", "你叫张三");
        assertThat(chatModel.prompts).hasSize(2);
        assertThat(chatModel.prompts.get(1).getInstructions())
                .extracting(message -> message.getText())
                .containsExactly("我叫张三", "已记录", "我叫什么名字？");
    }

    @Test
    void conversationIdIsRequiredForEveryCall() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        ChatClient chatClient = ChatClient.builder(new RecordingChatModel())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();

        assertThatThrownBy(() -> chatClient.prompt()
                .user("缺少会话 ID")
                .call()
                .content())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId cannot be null");
    }

    @Test
    void builderUsesOrderMethodPublishedInSpringAi200() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();

        var advisor = MessageChatMemoryAdvisor.builder(memory)
                .order(321)
                .build();

        assertThat(advisor.getOrder()).isEqualTo(321);
    }

    private static final class RecordingChatModel implements ChatModel {

        private final List<Prompt> prompts = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompts.add(prompt);
            String lastUserText = prompt.getUserMessage().getText();
            boolean containsName = prompt.getInstructions().stream()
                    .anyMatch(message -> message.getText().contains("我叫张三"));
            String answer = lastUserText.contains("叫什么名字") && containsName
                    ? "你叫张三"
                    : "已记录";
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage(answer))));
        }
    }
}
