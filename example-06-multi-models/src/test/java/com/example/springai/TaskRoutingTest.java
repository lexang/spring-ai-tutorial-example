package com.example.springai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskRoutingTest {
    @Test
    void planDoesNotCallModelsAndAnalyzeSelectsSeparateProvider() {
        ChatClient forbidden = ChatClient.create(prompt -> { throw new AssertionError("预览不应请求模型"); });
        var service = new TaskRoutingService(forbidden, forbidden, forbidden, forbidden);
        assertThat(service.plan(TaskRoutingService.Task.ANALYZE).clientId()).isEqualTo("minimax");
        assertThat(service.plan(TaskRoutingService.Task.SHORT_ANSWER).clientId()).isEqualTo("fast");
    }

    @Test
    void chatInvokesOnlySelectedClientAndReportsActualResponseModel() {
        AtomicInteger primary = new AtomicInteger();
        AtomicInteger secondary = new AtomicInteger();
        ChatClient openAi = ChatClient.create(prompt -> { primary.incrementAndGet(); return response("primary-fixture"); });
        ChatClient other = ChatClient.create(prompt -> { secondary.incrementAndGet(); return response("secondary-fixture"); });
        var result = new TaskRoutingService(openAi, openAi, openAi, other).chat(TaskRoutingService.Task.ANALYZE, "分析问题");
        assertThat(primary).hasValue(0);
        assertThat(secondary).hasValue(1);
        assertThat(result.responseModel()).isEqualTo("secondary-fixture");
        assertThat(result.usageReported()).isFalse();
        assertThat(result.totalTokens()).isNull();
    }

    @Test
    void invalidTasksAndBlankMessageAre400AndProviderExceptionIs502() throws Exception {
        ChatModel failing = prompt -> { throw new IllegalArgumentException("供应商敏感错误"); };
        ChatClient client = ChatClient.create(failing);
        var mvc = MockMvcBuilders.standaloneSetup(new TaskRoutingController(new TaskRoutingService(client, client, client, client)))
                .setControllerAdvice(new MultiModelExceptionHandler()).build();
        for (String body : List.of("{", "{}", "{\"task\":\"INVALID\"}")) {
            mvc.perform(post("/api/routing/plan").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/routing/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"task\":\"GENERAL\",\"message\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/routing/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"task\":\"GENERAL\",\"message\":\"问题\"}"))
                .andExpect(status().isBadGateway());
    }

    private static org.springframework.ai.chat.model.ChatResponse response(String model) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(new AssistantMessage("回答"))),
                ChatResponseMetadata.builder().model(model).build());
    }
}
