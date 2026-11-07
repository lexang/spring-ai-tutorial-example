package com.example.springai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatRequestControllerTest {

    @Test
    void requestOptionsAndSelectedDefaultsAreUsedWithoutMutatingOtherClient() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel model = prompt -> { captured.set(prompt); return response("回答", true); };
        var controller = new ChatRequestController(ChatClient.builder(model));
        var reply = controller.chat(new ChatRequestController.ChatRequest("解释依赖注入", 60, 0.2,
                ChatRequestController.ReplyStyle.CONCISE));
        assertThat(captured.get().getSystemMessage().getText()).contains("三句话");
        assertThat(captured.get().getOptions().getMaxTokens()).isEqualTo(60);
        assertThat(captured.get().getOptions().getTemperature()).isEqualTo(0.2);
        assertThat(reply.usage().totalTokens()).isEqualTo(15);
        assertThat(reply.requestId()).isNotBlank();
        controller.chat(new ChatRequestController.ChatRequest("另一个问题", null, null, null));
        assertThat(captured.get().getSystemMessage().getText()).contains("准确解释").doesNotContain("三句话");
        assertThat(captured.get().getOptions().getMaxTokens()).isEqualTo(500);
    }

    @Test
    void unknownUsageIsNotReportedAsZeroAndBlankAnswerFails() {
        var controller = new ChatRequestController(ChatClient.builder(prompt -> response("回答", false)));
        var reply = controller.chat(request());
        assertThat(reply.usage().reported()).isFalse();
        assertThat(reply.usage().totalTokens()).isNull();
        var blank = new ChatRequestController(ChatClient.builder(prompt -> response(" ", true)));
        assertThatThrownBy(() -> blank.chat(request())).isInstanceOf(EmptyChatResponseException.class);
    }

    @Test
    void invalidRequestIsRejectedBeforeModelCall() {
        ChatModel model = prompt -> { throw new AssertionError("无效请求不得访问模型"); };
        var controller = new ChatRequestController(ChatClient.builder(model));
        assertThatThrownBy(() -> controller.chat(new ChatRequestController.ChatRequest(" ", 50, null, null)))
                .isInstanceOf(InvalidDemoRequestException.class);
        assertThatThrownBy(() -> controller.chat(new ChatRequestController.ChatRequest("问题", 5000, null, null)))
                .isInstanceOf(InvalidDemoRequestException.class);
        assertThatThrownBy(() -> controller.chat(new ChatRequestController.ChatRequest("问题", 50, Double.NaN, null)))
                .isInstanceOf(InvalidDemoRequestException.class);
    }

    @Test
    void eachStreamSubscriptionCreatesFreshChainAndKeepsUsageOnlyChunk() {
        AtomicInteger subscriptions = new AtomicInteger();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) { throw new AssertionError("本例测试流式调用"); }
            public Flux<ChatResponse> stream(Prompt prompt) {
                subscriptions.incrementAndGet();
                ChatResponse usageOnly = new ChatResponse(List.of(), ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(10, 5, 15)).build());
                return Flux.just(response("第一段", false), response("第二段", false), usageOnly);
            }
        };
        var controller = new ChatRequestController(ChatClient.builder(model));
        var stream = controller.stream(request());
        assertThat(subscriptions).hasValue(0);
        for (int i = 0; i < 2; i++) {
            var chunks = stream.collectList().block();
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(2).content()).isEmpty();
            assertThat(chunks.get(2).usage().totalTokens()).isEqualTo(15);
        }
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void bindingAndProviderFailuresHaveDifferentHttpStatus() throws Exception {
        ChatModel model = prompt -> { throw new IllegalStateException("供应商私有信息"); };
        var mvc = MockMvcBuilders.standaloneSetup(new ChatRequestController(ChatClient.builder(model)))
                .setControllerAdvice(new ChatClientExceptionHandler()).build();
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"问题\",\"style\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"问题\"}"))
                .andExpect(status().isBadGateway());
    }

    private static ChatRequestController.ChatRequest request() {
        return new ChatRequestController.ChatRequest("问题", null, null, null);
    }

    private static ChatResponse response(String content, boolean usageReported) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().id("response-03").model("fixture");
        if (usageReported) metadata.usage(new DefaultUsage(10, 5, 15));
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason("STOP").build())), metadata.build());
    }
}
