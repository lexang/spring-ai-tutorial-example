package com.example.springai;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private RecordingModel model;
    private ChatController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.model = new RecordingModel();
        this.controller = new ChatController(ChatClient.builder(this.model));
        this.mvc = MockMvcBuilders.standaloneSetup(this.controller)
                .setControllerAdvice(new GettingStartedExceptionHandler()).build();
    }

    @Test
    void syncCallUsesUserMessage() {
        assertThat(this.controller.chat("你好")).isEqualTo("测试回答");
        assertThat(this.model.prompt.getUserMessage().getText()).isEqualTo("你好");
    }

    @Test
    void blankInputIsRejectedBeforeCallingModel() throws Exception {
        this.mvc.perform(get("/chat").param("message", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("message 不能为空"));
        assertThat(this.model.calls).isZero();
    }

    @Test
    void nonFiniteTemperatureIsRejectedBeforeCallingModel() throws Exception {
        this.mvc.perform(get("/chat/creative").param("message", "你好").param("temperature", "NaN"))
                .andExpect(status().isBadRequest());
        assertThat(this.model.calls).isZero();
    }

    @Test
    void invalidNumericParameterHasChineseError() throws Exception {
        this.mvc.perform(get("/chat/creative").param("message", "你好").param("temperature", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数缺失或类型不正确"));
    }

    @Test
    void missingRequiredParameterHasChineseError() throws Exception {
        this.mvc.perform(get("/chat/safe")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数缺失或类型不正确"));
    }

    @Test
    void emptyModelResponseIsBadGateway() throws Exception {
        this.model.response = new ChatResponse(List.of());
        this.mvc.perform(get("/chat/safe").param("message", "你好"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("模型没有返回文本内容"));
    }

    @Test
    void providerFailureDoesNotLeakItsMessage() throws Exception {
        this.model.failure = new IllegalStateException("供应商内部地址和敏感请求内容");
        this.mvc.perform(get("/chat").param("message", "你好"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("AI 服务暂时不可用，请稍后重试"));
    }

    @Test
    void creativeCallPassesOptionsBuilder() {
        assertThat(this.controller.chatCreative("写首诗", 0.2)).isEqualTo("测试回答");
        assertThat(this.model.prompt.getOptions().getTemperature()).isEqualTo(0.2);
    }

    @Test
    void streamIsLazyAndPreservesChunkBoundaries() {
        this.model.chunks = Flux.just(response("第一块"), response("第二块"));
        Flux<String> stream = this.controller.chatStream("你好");
        assertThat(this.model.calls).isZero();
        assertThat(stream.collectList().block()).containsExactly("第一块", "第二块");
        assertThat(this.model.calls).isEqualTo(1);
    }

    @Test
    void emptyStreamFailsInsteadOfReportingSuccess() {
        this.model.chunks = Flux.empty();
        assertThatThrownBy(() -> this.controller.chatStream("你好").collectList().block())
                .isInstanceOf(AiServiceException.class).hasMessage("模型没有返回文本内容");
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static final class RecordingModel implements ChatModel {
        private Prompt prompt;
        private int calls;
        private ChatResponse response = response("测试回答");
        private RuntimeException failure;
        private Flux<ChatResponse> chunks = Flux.just(response("测试回答"));

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            this.calls++;
            if (this.failure != null) throw this.failure;
            return this.response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.prompt = prompt;
            this.calls++;
            return this.chunks;
        }
    }
}
