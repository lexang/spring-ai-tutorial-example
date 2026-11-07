package com.example.springai;

import java.util.List;

import com.example.springai.config.EvaluationConfiguration;
import com.example.springai.evaluation.EvaluationService;
import com.example.springai.web.ApiExceptionHandler;
import com.example.springai.web.EvaluationController;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveJudgeWiringTest {
    @Test void liveModeUsesTheProvidedModelInsteadOfTheDefaultJudge() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().model("wiring-fixture").build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("YES")))));
        new ApplicationContextRunner().withUserConfiguration(EvaluationConfiguration.class, EvaluationService.class)
                .withPropertyValues("app.evaluation.offline=false").withBean(ChatModel.class, () -> model)
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).doesNotHaveBean("evaluationChatModel");
                    var result = context.getBean(EvaluationService.class).evaluateRelevancy("问题", List.of("资料"), "回答");
                    assertThat(result.mode()).isEqualTo("model-judge");
                    assertThat(result.pass()).isTrue();
                    verify(model).call(any(Prompt.class));
                });
    }

    @Test void judgeExceptionsReturn502WithoutPrivateMessages() throws Exception {
        var service = mock(EvaluationService.class);
        when(service.evaluateRelevancy(anyString(), anyList(), anyString())).thenThrow(
                new EvaluationService.EvaluationFailureException(new IllegalStateException("private-judge-detail")));
        var mvc = MockMvcBuilders.standaloneSetup(new EvaluationController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        String body = mvc.perform(post("/api/evaluations/relevancy").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"问题\",\"context\":[\"资料\"],\"response\":\"回答\"}"))
                .andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("private-judge-detail");
    }
}
