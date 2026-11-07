package com.example.promptengineering;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class LivePromptContractTest {
    @Test
    void invalidAnswersAreExcludedAndTiesDoNotSelectFirstSample() {
        AtomicInteger index = new AtomicInteger();
        List<String> outputs = List.of("无 FINAL 标记", "FINAL= A", "FINAL=B");
        var controller = controller(prompt -> response(outputs.get(index.getAndIncrement())));
        var result = controller.selfConsistency(new LivePromptController.SelfConsistencyRequest("选择答案", 3));
        assertThat(result.invalidSamples()).isEqualTo(1);
        assertThat(result.vote().tie()).isTrue();
        assertThat(result.vote().answer()).isNull();
        assertThat(result.samples()).containsExactly("A", "B");
    }

    @Test
    void rejectsSampleBoundsBeforeCallingModelAndRejectsAllInvalidResults() {
        AtomicInteger calls = new AtomicInteger();
        var controller = controller(prompt -> { calls.incrementAndGet(); return response("FINAL="); });
        assertThatThrownBy(() -> controller.selfConsistency(
            new LivePromptController.SelfConsistencyRequest("问题", 8)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> controller.selfConsistency(
            new LivePromptController.SelfConsistencyRequest("问题", 2)))
            .isInstanceOf(LivePromptController.ModelOperationException.class);
    }

    @Test
    void providerErrorsAndMalformedJsonAreSeparated() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller(prompt -> {
            throw new IllegalArgumentException("供应商敏感详情");
        })).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/live/zero-shot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"review\":\"一般\"}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("message").value("AI 服务未返回可用结果，请稍后重试"));
        mvc.perform(post("/api/live/zero-shot").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void stepBackFeedsGeneratedBackgroundIntoSecondCall() {
        AtomicInteger calls = new AtomicInteger();
        var controller = controller(prompt -> {
            if (calls.incrementAndGet() == 1) return response("幂等键与重试边界");
            assertThat(prompt.getContents()).contains("幂等键与重试边界", "重复消费");
            return response("使用幂等键");
        });
        assertThat(controller.stepBack(new LivePromptController.QuestionRequest("如何处理重复消费")))
                .isEqualTo("使用幂等键");
        assertThat(calls).hasValue(2);
    }

    private LivePromptController controller(ChatModel model) {
        return new LivePromptController(ChatClient.builder(model), new PromptPatternCatalog());
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
