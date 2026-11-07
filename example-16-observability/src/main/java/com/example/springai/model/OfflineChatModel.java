package com.example.springai.model;

import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;

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
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 确定性离线 ChatModel。
 *
 * <p>真实供应商模型通常在自己的实现内部创建同名 observation；这里用同一套
 * 官方 observation API 让示例在没有密钥时也能验证模型层指标。</p>
 */
public final class OfflineChatModel implements ChatModel {

    private static final String PROVIDER = "offline";

    private static final String MODEL = "offline-demo";

    private final ObservationRegistry observationRegistry;

    public OfflineChatModel(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
            .prompt(prompt)
            .provider(PROVIDER)
            .build();

        return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
            .observation(null, new DefaultChatModelObservationConvention(), () -> observationContext,
                this.observationRegistry)
            .observe(() -> {
                ChatResponse response = response("离线模型响应：" + prompt.getUserMessage().getText(), false);
                observationContext.setResponse(response);
                return response;
            });
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.deferContextual(contextView -> {
            ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(PROVIDER)
                .streaming(true)
                .build();
            Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
                null, new DefaultChatModelObservationConvention(), () -> observationContext,
                this.observationRegistry);
            Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
            observation.parentObservation(parentObservation);
            try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
                    : Observation.Scope.NOOP) {
                observation.start();
            }

            return Flux.just(
                    response("离线流式响应：第一段。", false),
                    response("第二段。", true))
                .doOnNext(observationContext::setResponse)
                .doOnError(observation::error)
                .doFinally(signal -> observation.stop())
                .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, observation));
        });
    }

    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
        return org.springframework.ai.chat.prompt.ChatOptions.builder().model(MODEL).build();
    }

    private ChatResponse response(String text, boolean finalChunk) {
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finalChunk ? "STOP" : null)
            .build();
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
            .id("offline-response")
            .model(MODEL)
            .usage(new DefaultUsage(8, Math.max(1, text.length() / 2)))
            .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text), generationMetadata)), metadata);
    }

}
