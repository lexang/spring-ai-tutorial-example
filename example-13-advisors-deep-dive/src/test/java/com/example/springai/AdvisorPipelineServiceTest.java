package com.example.springai;

import com.example.springai.service.AdvisorPipelineService;
import com.example.springai.support.AdvisorOperationException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvisorPipelineServiceTest {

    @Test
    void blankResponseCannotBecomeSuccessfulByAddingALabel() {
        ChatModel blankModel = new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) {
                return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(""))));
            }
            @Override public Flux<ChatResponse> stream(Prompt prompt) { return Flux.just(call(prompt)); }
        };
        AdvisorPipelineService service = new AdvisorPipelineService(provider(blankModel));
        assertThatThrownBy(() -> service.call("空文本"))
                .isInstanceOf(AdvisorOperationException.class);
        StepVerifier.create(service.stream("空文本"))
                .expectError(AdvisorOperationException.class).verify();
    }

    @Test
    void malformedJsonHasAChineseBadRequest() throws Exception {
        var controller = new com.example.springai.controller.AdvisorController(
                new AdvisorPipelineService(provider(null)));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.example.springai.support.AdvisorExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/advisors/call").contentType("application/json").content("{"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value("请求体格式不正确"));
    }

    @Test
    void nullModelResponseBecomesAChineseOperationError() {
        ChatModel nullResponseModel = prompt -> null;
        AdvisorPipelineService service = new AdvisorPipelineService(provider(nullResponseModel));

        assertThatThrownBy(() -> service.call("返回空响应"))
            .isInstanceOf(AdvisorOperationException.class)
            .hasMessage("模型返回空响应，请检查供应商响应格式");
    }

    @Test
    void emptyStreamingResponseBecomesAChineseOperationError() {
        ChatModel emptyStreamModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return null;
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.empty();
            }
        };
        AdvisorPipelineService service = new AdvisorPipelineService(provider(emptyStreamModel));

        StepVerifier.create(service.stream("返回空流"))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(AdvisorOperationException.class)
                .hasMessage("模型未返回任何流式内容，请检查供应商响应格式"))
            .verify();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatModel> provider(ChatModel chatModel) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        return provider;
    }
}
