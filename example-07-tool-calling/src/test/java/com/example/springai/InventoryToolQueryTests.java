package com.example.springai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.springai.inventory.InventoryTools;
import com.example.springai.inventory.ToolQueryController;
import com.example.springai.inventory.ToolQueryService;
import com.example.springai.inventory.ToolQueryService.Mode;
import com.example.springai.inventory.ToolQueryService.QueryRequest;
import com.example.springai.inventory.ToolQueryService.Status;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用实际 Spring AI Advisor、Callback 和 Manager 验证，不调用外部模型。 */
class InventoryToolQueryTests {

    @Test void schemaContainsSkuButNotServerContext() {
        String schema = ToolCallbacks.from(new InventoryTools())[0].getToolDefinition().inputSchema();
        assertThat(schema).contains("sku").doesNotContain("warehouseId", "toolContext");
    }

    @Test void sameSkuIsScopedToTheServerWarehouse() {
        InventoryTools tools = new InventoryTools();
        assertThat(tools.queryStock("BOOK-001", context("demo-shanghai")).available()).isEqualTo(12);
        assertThat(tools.queryStock("BOOK-001", context("demo-beijing")).available()).isEqualTo(3);
    }

    @Test void invalidSkuCannotBypassJavaValidation() {
        assertThatThrownBy(() -> new InventoryTools().queryStock("../secret", context("demo-shanghai")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("商品编号");
    }

    @Test void missingWarehouseFailsClosed() {
        assertThatThrownBy(() -> new InventoryTools().queryStock("BOOK-001", new ToolContext(Map.of("other", "x"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("仓库");
    }

    @Test void autoExecutesToolAndSendsPairedHistoryBack() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"), answer("演示库存为 12"));
        CountingTools tools = new CountingTools();
        var result = service(model, tools, 6, 3).query(new QueryRequest("查询 BOOK-001", Mode.AUTO));
        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(tools.count).hasValue(1);
        assertThat(model.prompts).hasSize(2);
        assertThat(model.prompts.get(1).getInstructions()).hasSize(4);
        assertThat(model.prompts.get(1).getInstructions().get(3)).isInstanceOf(ToolResponseMessage.class);
        assertThat(result.usage().totalTokens()).isEqualTo(10);
        assertThat(result.usage().complete()).isTrue();
    }

    @Test void manualExecutesTheSameToolAndAccumulatesUsage() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"), answer("查询完成"));
        CountingTools tools = new CountingTools();
        var result = service(model, tools, 6, 3).query(new QueryRequest("查询 BOOK-001", Mode.MANUAL));
        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.usage().modelCalls()).isEqualTo(2);
        assertThat(result.usage().promptTokens()).isEqualTo(6);
        assertThat(result.usage().completionTokens()).isEqualTo(4);
        assertThat(tools.count).hasValue(1);
        assertThat(model.prompts.get(1).getInstructions()).hasSize(4);
    }

    @Test void planOnlyNeverExecutesATool() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"));
        CountingTools tools = new CountingTools();
        var result = service(model, tools, 6, 3).query(new QueryRequest("查询 BOOK-001", Mode.PLAN_ONLY));
        assertThat(result.status()).isEqualTo(Status.EXECUTION_SKIPPED);
        assertThat(result.pendingTools()).containsExactly("queryStock");
        assertThat(tools.count).hasValue(0);
        assertThat(model.prompts).hasSize(1);
    }

    @Test void ordinaryAnswerDoesNotNeedATool() {
        ScriptedModel model = new ScriptedModel(answer("请提供商品编号"));
        CountingTools tools = new CountingTools();
        var result = service(model, tools, 6, 3).query(new QueryRequest("查库存", null));
        assertThat(result.answer()).isEqualTo("请提供商品编号");
        assertThat(tools.count).hasValue(0);
    }

    @Test void directReturnSkipsSecondModelCallButKeepsFirstUsage() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"));
        var result = service(model, new CountingTools(), 6, 3).query(new QueryRequest("查询 BOOK-001", Mode.DIRECT));
        assertThat(result.status()).isEqualTo(Status.DIRECT);
        assertThat(result.answer()).contains("BOOK-001", "12", "固定演示数据");
        assertThat(result.finishReason()).isEqualTo("returnDirect");
        assertThat(result.usage().modelCalls()).isEqualTo(1);
        assertThat(result.usage().totalTokens()).isEqualTo(5);
    }

    @Test void manualLimitCountsHistoryAndDoesNotExecuteAgain() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"), toolCall("queryStock"));
        CountingTools tools = new CountingTools();
        var result = service(model, tools, 6, 1).query(new QueryRequest("反复查询", Mode.MANUAL));
        assertThat(result.status()).isEqualTo(Status.TOOL_CALL_LIMIT);
        assertThat(tools.count).hasValue(1);
        assertThat(result.usage().modelCalls()).isEqualTo(2);
    }

    @Test void autoLimitIsAFinalGenerationNotAnEscapedException() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"), toolCall("queryStock"));
        CountingTools tools = new CountingTools();
        var result = service(model, tools, 6, 1).query(new QueryRequest("反复查询", Mode.AUTO));
        assertThat(result.status()).isEqualTo(Status.TOOL_CALL_LIMIT);
        assertThat(result.finishReason()).isEqualTo(ToolCallLimitExceededException.FINISH_REASON);
        assertThat(tools.count).hasValue(1);
        assertThat(result.answer()).doesNotContain("Tool call limit");
    }

    @Test void modelBudgetStopsBeforeTheNextAutoRequest() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"));
        CountingTools tools = new CountingTools();
        var result = service(model, tools, 1, 3).query(new QueryRequest("查询", Mode.AUTO));
        assertThat(result.status()).isEqualTo(Status.MODEL_CALL_LIMIT);
        assertThat(model.prompts).hasSize(1);
        assertThat(tools.count).hasValue(1); // 已完成的只读操作不会回滚或重放
    }

    @Test void modelBudgetAlsoAppliesToManualRequests() {
        ScriptedModel model = new ScriptedModel(toolCall("queryStock"));
        var result = service(model, new CountingTools(), 1, 3).query(new QueryRequest("查询", Mode.MANUAL));
        assertThat(result.status()).isEqualTo(Status.MODEL_CALL_LIMIT);
        assertThat(model.prompts).hasSize(1);
    }

    @Test void unknownUsageIsNotReportedAsZero() {
        ChatResponse noUsage = new ChatResponse(List.of(new Generation(new AssistantMessage("请提供编号"))));
        var result = service(new ScriptedModel(noUsage), new CountingTools(), 6, 3)
                .query(new QueryRequest("查询", Mode.AUTO));
        assertThat(result.usage().totalTokens()).isNull();
        assertThat(result.usage().complete()).isFalse();
        assertThat(result.usage().reportedCalls()).isZero();
    }

    @Test void missingOneRoundKeepsKnownUsageButMarksItPartial() {
        ChatResponse noUsage = new ChatResponse(List.of(new Generation(new AssistantMessage("查询完成"))));
        var result = service(new ScriptedModel(toolCall("queryStock"), noUsage), new CountingTools(), 6, 3)
                .query(new QueryRequest("查询", Mode.MANUAL));
        assertThat(result.usage().totalTokens()).isEqualTo(5);
        assertThat(result.usage().reportedCalls()).isEqualTo(1);
        assertThat(result.usage().complete()).isFalse();
    }

    @Test void requestStateIsNotSharedAcrossCalls() {
        ScriptedModel model = new ScriptedModel(answer("一"), answer("二"));
        var service = service(model, new CountingTools(), 1, 3);
        assertThat(service.query(new QueryRequest("一", Mode.AUTO)).usage().modelCalls()).isEqualTo(1);
        assertThat(service.query(new QueryRequest("二", Mode.AUTO)).usage().modelCalls()).isEqualTo(1);
    }

    @Test void resolverCannotExecuteAToolAbsentFromRequest() {
        var extra = ToolCallbacks.from(new ExtraTools())[0];
        var manager = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(List.of(extra))).build();
        Prompt prompt = new Prompt("查询", ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(new InventoryTools())).build());
        assertThatThrownBy(() -> manager.executeToolCalls(prompt, toolCall("extra")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No ToolCallback");
    }

    @Test void runtimeToolFailureCanBeFeedbackButCheckedAndErrorAlwaysEscape() {
        var definition = ToolCallbacks.from(new InventoryTools())[0].getToolDefinition();
        var processor = new DefaultToolExecutionExceptionProcessor(false);
        assertThat(processor.process(new ToolExecutionException(definition, new IllegalArgumentException("编号无效"))))
                .contains("编号无效");
        assertThatThrownBy(() -> processor.process(new ToolExecutionException(definition, new IOException("失败"))))
                .isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> processor.process(new ToolExecutionException(definition, new AssertionError("失败"))))
                .isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> new DefaultToolExecutionExceptionProcessor(true)
                .process(new ToolExecutionException(definition, new IllegalArgumentException("失败"))))
                .isInstanceOf(ToolExecutionException.class);
    }

    @Test void badHttpInputAndProviderFailureHaveDifferentStatuses() throws Exception {
        ScriptedModel model = new ScriptedModel();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ToolQueryController(service(model, new CountingTools(), 6, 3)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/tools/query").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tools/query").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"查库存\",\"mode\":\"UNKNOWN\"}")).andExpect(status().isBadRequest());
        model.failure = new IllegalArgumentException("供应商正文包含 secret-key");
        String body = mvc.perform(post("/api/tools/query").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"查库存\"}")).andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret-key", "供应商正文");
        assertThat(model.prompts).hasSize(1);
    }

    @Test void emptyModelResponseIsAServiceFailure() {
        assertThatThrownBy(() -> service(new ScriptedModel(new ChatResponse(List.of())), new CountingTools(), 6, 3)
                .query(new QueryRequest("查询", Mode.MANUAL)))
                .isInstanceOf(ToolQueryService.ToolQueryFailureException.class);
    }

    private static ToolQueryService service(ScriptedModel model, InventoryTools tools, int maxCalls, int toolLimit) {
        var manager = DefaultToolCallingManager.builder().maxCallsPerTool(toolLimit).maxTotalToolCalls(6).build();
        var builder = ChatClient.builder(model).defaultAdvisors(ToolCallingAdvisor.builder()
                .toolCallingManager(manager).build());
        return new ToolQueryService(model, builder, manager, tools, maxCalls);
    }

    private static ToolContext context(String warehouse) { return new ToolContext(Map.of("warehouseId", warehouse)); }
    private static ChatResponse answer(String text) {
        return response(new AssistantMessage(text));
    }
    private static ChatResponse toolCall(String name) {
        return response(AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", name, "{\"sku\":\"BOOK-001\"}"))).build());
    }
    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)), ChatResponseMetadata.builder()
                .model("离线固定模型").usage(new DefaultUsage(3, 2, 5)).build());
    }

    static class ScriptedModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();
        final List<ChatResponse> responses;
        RuntimeException failure;
        ScriptedModel(ChatResponse... responses) { this.responses = List.of(responses); }
        @Override public ChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
        @Override public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            if (failure != null) { throw failure; }
            return responses.get(prompts.size() - 1);
        }
    }

    static class CountingTools extends InventoryTools {
        final AtomicInteger count = new AtomicInteger();
        @Override @Tool(description = "查询演示库存，只读")
        public StockSnapshot queryStock(String sku, ToolContext context) {
            count.incrementAndGet();
            return super.queryStock(sku, context);
        }
    }
    static class ExtraTools {
        @Tool(description = "请求之外的工具") public String extra(String sku) { return "不应该执行"; }
    }
}
