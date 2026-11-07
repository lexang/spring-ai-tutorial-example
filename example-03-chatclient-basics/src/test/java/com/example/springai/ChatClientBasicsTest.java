package com.example.springai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatClientBasicsTest {

    @Test
    void callOnlySelectsSynchronousModeUntilTerminalMethodRuns() {
        RecordingChatModel model = new RecordingChatModel();
        ChatClient chatClient = ChatClient.create(model);

        ChatClient.CallResponseSpec responseSpec = chatClient.prompt()
                .user("延迟执行测试")
                .call();

        assertThat(model.callCount).hasValue(0);
        assertThat(responseSpec.content()).isEqualTo("离线响应");
        assertThat(model.callCount).hasValue(1);
    }

    @Test
    void optionsEndpointUsesBuilderAndKeepsTokenLimitFieldsMutuallyExclusive() {
        RecordingChatModel model = new RecordingChatModel();
        ChatClientController controller = new ChatClientController(ChatClient.builder(model));

        controller.withOptions("普通模型", 0.6, 321, false);
        OpenAiChatOptions normalOptions = (OpenAiChatOptions) model.lastPrompt.getOptions();
        assertThat(normalOptions.getTemperature()).isEqualTo(0.6);
        assertThat(normalOptions.getMaxTokens()).isEqualTo(321);
        assertThat(normalOptions.getMaxCompletionTokens()).isNull();

        controller.withOptions("推理模型", -99.0, 654, true);
        OpenAiChatOptions reasoningOptions = (OpenAiChatOptions) model.lastPrompt.getOptions();
        assertThat(reasoningOptions.getTemperature()).isNull();
        assertThat(reasoningOptions.getMaxTokens()).isNull();
        assertThat(reasoningOptions.getMaxCompletionTokens()).isEqualTo(654);
    }

    @Test
    void optionsEndpointRejectsInvalidValuesBeforeCallingModel() {
        RecordingChatModel model = new RecordingChatModel();
        ChatClientController controller = new ChatClientController(ChatClient.builder(model));

        assertThatThrownBy(() -> controller.withOptions("测试", Double.NaN, 100, false))
                .isInstanceOf(InvalidDemoRequestException.class)
                .hasMessageContaining("temperature");
        assertThatThrownBy(() -> controller.withOptions("测试", 0.7, 0, false))
                .isInstanceOf(InvalidDemoRequestException.class)
                .hasMessageContaining("maxTokens");
        assertThat(model.callCount).hasValue(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void messageMetadataIsReportedSeparatelyFromProviderResponseMetadata() {
        RecordingChatModel model = new RecordingChatModel();
        ChatClientController controller = new ChatClientController(ChatClient.builder(model));

        Map<String, Object> result = controller.withMetadata("你好");

        Map<String, Object> requestMetadata = (Map<String, Object>) result.get("requestMessageMetadata");
        Map<String, Object> systemMetadata = (Map<String, Object>) requestMetadata.get("system");
        Map<String, Object> userMetadata = (Map<String, Object>) requestMetadata.get("user");
        assertThat(systemMetadata)
                .containsEntry("assistantName", "教程助手")
                .containsEntry("messageType", MessageType.SYSTEM);
        assertThat(userMetadata)
                .containsEntry("source", "web-api")
                .containsEntry("messageType", MessageType.USER);
        assertThat(result).containsKeys("responseMetadata", "advisorContextKeys");
    }

    @Test
    void fullResponseFailsClearlyWhenProviderReturnsNoGeneration() {
        RecordingChatModel model = new RecordingChatModel();
        model.response = new ChatResponse(List.of());
        ChatClientController controller = new ChatClientController(ChatClient.builder(model));

        assertThatThrownBy(() -> controller.fullResponse("空结果"))
                .isInstanceOf(EmptyChatResponseException.class)
                .hasMessageContaining("生成结果");
    }

    private static final class RecordingChatModel implements ChatModel {

        private final AtomicInteger callCount = new AtomicInteger();

        private Prompt lastPrompt;

        private ChatResponse response = response("离线响应");

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            this.callCount.incrementAndGet();
            return this.response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.lastPrompt = prompt;
            return Flux.just(this.response);
        }

        @Override
        public OpenAiChatOptions getOptions() {
            return OpenAiChatOptions.builder().build();
        }

        private static ChatResponse response(String content) {
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .model("offline-model")
                    .usage(new DefaultUsage(4, 2, 6))
                    .build();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
        }

    }

}
