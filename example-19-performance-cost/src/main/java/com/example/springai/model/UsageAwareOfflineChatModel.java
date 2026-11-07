package com.example.springai.model;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * 不依赖外部服务的确定性 ChatModel。
 *
 * <p>真实供应商实现会在模型调用时填充 Usage；此模型用同一套
 * ChatResponseMetadata 结构产生可重复的本地数据，便于测试预算计算。</p>
 */
public final class UsageAwareOfflineChatModel implements ChatModel {

    private static final String MODEL = "offline-cost-model";

    private final ObservationRegistry observationRegistry;

    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

    public UsageAwareOfflineChatModel(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
            .prompt(prompt)
            .provider("offline")
            .build();

        return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
            .observation(null, new DefaultChatModelObservationConvention(), () -> observationContext,
                this.observationRegistry)
            .observe(() -> {
                ChatResponse response = response(prompt.getUserMessage().getText());
                observationContext.setResponse(response);
                return response;
            });
    }

    @Override
    public ChatOptions getOptions() {
        return ChatOptions.builder().model(MODEL).build();
    }

    private ChatResponse response(String promptText) {
        int promptTokens = Math.max(1, this.tokenCountEstimator.estimate(promptText));
        int completionTokens = Math.max(1, Math.min(24, promptText.length() / 4));
        Map<String, Object> nativeUsage = Map.of(
            "provider", "offline",
            "promptTokens", promptTokens,
            "completionTokens", completionTokens);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
            .id("offline-cost-response")
            .model(MODEL)
            .usage(new DefaultUsage(promptTokens, completionTokens, null, nativeUsage))
            .build();
        Generation generation = new Generation(
            new AssistantMessage("离线模型响应：已根据预算示例处理“" + promptText + "”。"),
            ChatGenerationMetadata.builder().finishReason("STOP").build());
        return new ChatResponse(List.of(generation), metadata);
    }

}
