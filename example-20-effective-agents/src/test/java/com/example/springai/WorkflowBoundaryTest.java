package com.example.springai;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.springai.service.AgentWorkflowService;
import com.example.springai.web.AgentController;
import com.example.springai.web.ApiExceptionHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class WorkflowBoundaryTest {
    @Test
    void unknownRouteIsNotSilentlyMappedToGeneral() {
        withService(prompt -> response("billing or technical"), service ->
            assertThatThrownBy(() -> service.route("问题"))
                .isInstanceOf(AgentWorkflowService.ModelOperationException.class));
    }

    @Test
    void iterationLimitIsExplicitAndInitialDraftIsPreserved() {
        withService(prompt -> response(prompt.getContents().startsWith("[EVALUATE]")
            ? "{\"pass\":false,\"feedback\":\"仍需修改\"}" : "初稿内容"), service -> {
                var result = service.evaluateAndOptimize("生成方案", 1);
                assertThat(result.accepted()).isFalse();
                assertThat(result.stopReason()).isEqualTo("ITERATION_LIMIT");
                assertThat(result.draft()).isEqualTo("初稿内容");
                assertThat(result.evaluations()).hasSize(1);
            });
    }

    @Test
    void missingEvaluationAndInvalidPlanAreModelFailures() {
        withService(prompt -> response(prompt.getContents().startsWith("[GENERATE]")
            ? "初稿" : "{\"feedback\":\"缺少 pass\"}"), service ->
                assertThatThrownBy(() -> service.evaluateAndOptimize("方案", 2))
                    .isInstanceOf(AgentWorkflowService.ModelOperationException.class));
        withService(prompt -> response("{\"subtasks\":[\"\"]}"), service ->
            assertThatThrownBy(() -> service.orchestrate("任务"))
                .isInstanceOf(AgentWorkflowService.ModelOperationException.class));
    }

    @Test
    void oversizedChainIsRejectedBeforeAnyCall() {
        AtomicInteger calls = new AtomicInteger();
        withService(prompt -> { calls.incrementAndGet(); return response("结果"); }, service ->
            assertThatThrownBy(() -> service.chain("任务", java.util.Collections.nCopies(9, "步骤")))
                .isInstanceOf(IllegalArgumentException.class));
        assertThat(calls).hasValue(0);
    }

    @Test
    void modelFailureUses502WithoutProviderDetails() {
        withService(prompt -> { throw new IllegalArgumentException("密钥或供应商详情"); }, service -> {
            try {
                var mvc = MockMvcBuilders.standaloneSetup(new AgentController(service))
                    .setControllerAdvice(new ApiExceptionHandler()).build();
                mvc.perform(post("/api/agents/route").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"问题\"}"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("message").value("AI 服务未返回可用结果，请稍后重试"));
                mvc.perform(post("/api/agents/route").contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest());
            }
            catch (Exception exception) { throw new AssertionError(exception); }
        });
    }

    @Test
    void executorRejectionHasExplicitBusyStatus() {
        var executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        var service = new AgentWorkflowService(ChatClient.create(prompt -> response("结果")), executor);
        assertThatThrownBy(() -> service.parallel("任务", List.of("视角")))
            .isInstanceOf(AgentWorkflowService.WorkflowBusyException.class);
    }

    private void withService(ChatModel model, java.util.function.Consumer<AgentWorkflowService> check) {
        var executor = Executors.newFixedThreadPool(2);
        try { check.accept(new AgentWorkflowService(ChatClient.create(model), executor)); }
        finally { executor.shutdownNow(); }
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
