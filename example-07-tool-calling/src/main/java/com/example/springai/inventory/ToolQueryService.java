package com.example.springai.inventory;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 自动与手动模式使用相同的只读工具；每个请求独立记录预算和用量。 */
@Service
public class ToolQueryService {

    private static final String SYSTEM = """
            你是只读库存助手。查询库存时使用提供的工具，不编造库存，也不能下单或扣库存。
            工具数据来自固定演示仓库，不代表真实库存。缺少商品编号时先询问用户。
            """;
    // 这是服务端固定的教学上下文，不是认证实现；不能从模型生成的参数中取身份。
    private static final Map<String, Object> CONTEXT = Map.of("warehouseId", "demo-shanghai");

    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final ToolCallingManager manager;
    private final InventoryTools inventoryTools;
    private final int maxModelCalls;

    public ToolQueryService(ChatModel chatModel, ChatClient.Builder builder,
            ToolCallingManager manager, InventoryTools inventoryTools,
            @Value("${tutorial.tools.max-model-calls:6}") int maxModelCalls) {
        if (maxModelCalls < 1 || maxModelCalls > 20) {
            throw new IllegalArgumentException("模型调用预算必须在 1 到 20 之间");
        }
        this.chatModel = chatModel;
        this.chatClient = builder.clone().build();
        this.manager = manager;
        this.inventoryTools = inventoryTools;
        this.maxModelCalls = maxModelCalls;
    }

    public QueryResult query(QueryRequest request) {
        if (request == null || !StringUtils.hasText(request.question()) || request.question().length() > 2000) {
            throw new InvalidToolRequestException("问题不能为空，且不能超过 2000 个字符");
        }
        Mode mode = request.mode() == null ? Mode.AUTO : request.mode();
        String question = request.question().trim();
        Object tool = mode == Mode.DIRECT
                ? new InventoryTools.DirectInventoryTools(inventoryTools) : inventoryTools;
        ToolCallback[] callbacks = ToolCallbacks.from(tool);
        ToolCallingChatOptions.validateToolCallbacks(List.of(callbacks));
        ModelRounds rounds = new ModelRounds(maxModelCalls);
        try {
            if (mode == Mode.AUTO || mode == Mode.DIRECT) {
                ChatResponse response = chatClient.prompt()
                        .system(SYSTEM).user(question)
                        .tools((Object[]) callbacks).toolContext(CONTEXT)
                        .advisors(rounds).call().chatResponse();
                requireResponse(response);
                String finish = response.getResult().getMetadata().getFinishReason();
                if (ToolCallLimitExceededException.FINISH_REASON.equals(finish)) {
                    return result(Status.TOOL_CALL_LIMIT, "工具调用次数已达上限", finish, List.of(), rounds);
                }
                return result(ToolExecutionResult.FINISH_REASON.equals(finish) ? Status.DIRECT : Status.COMPLETED,
                        requireText(response), finish, List.of(), rounds);
            }
            return manually(question, mode, callbacks, rounds);
        }
        catch (ModelCallBudgetExceededException ex) {
            return result(Status.MODEL_CALL_LIMIT, "已达到模型调用预算，停止继续请求", "modelCallBudgetExceeded",
                    List.of(), rounds);
        }
        catch (ToolCallLimitExceededException ex) {
            // 部分工具可能已经执行；这里只返回终止状态，绝不重新执行整批工具。
            return result(Status.TOOL_CALL_LIMIT, "工具调用次数已达上限", ToolCallLimitExceededException.FINISH_REASON,
                    List.of(), rounds);
        }
        catch (RuntimeException ex) {
            // 模型、工具和协议异常不能冒充调用方的参数错误，也不回传供应商原文。
            throw new ToolQueryFailureException(ex);
        }
    }

    private QueryResult manually(String question, Mode mode, ToolCallback[] callbacks, ModelRounds rounds) {
        var options = ToolCallingChatOptions.builder().toolCallbacks(callbacks).toolContext(CONTEXT).build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM), new UserMessage(question)), options);
        while (true) {
            rounds.beforeCall();
            ChatResponse response = chatModel.call(prompt); // 2.0 中不会自动执行工具
            rounds.record(response);
            requireResponse(response);
            if (!response.hasToolCalls()) {
                return result(Status.COMPLETED, requireText(response),
                        response.getResult().getMetadata().getFinishReason(), List.of(), rounds);
            }
            if (mode == Mode.PLAN_ONLY) {
                List<String> pending = response.getResult().getOutput().getToolCalls().stream()
                        .map(call -> call.name()).toList();
                return result(Status.EXECUTION_SKIPPED, "仅查看工具请求，未执行工具", "executionSkipped", pending, rounds);
            }
            ToolExecutionResult execution = manager.executeToolCalls(prompt, response);
            if (execution.returnDirect()) {
                String answer = ToolExecutionResult.buildGenerations(execution).stream()
                        .map(generation -> generation.getOutput().getText())
                        .collect(java.util.stream.Collectors.joining("\n"));
                return result(Status.DIRECT, answer, ToolExecutionResult.FINISH_REASON, List.of(), rounds);
            }
            // 必须保留完整 turn：Assistant 工具请求与 ToolResponse 都不能丢。
            prompt = new Prompt(execution.conversationHistory(), options);
        }
    }

    private static QueryResult result(Status status, String answer, String finish, List<String> pending,
            ModelRounds rounds) {
        return new QueryResult(status, answer, finish, pending, rounds.summary());
    }

    private static void requireResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型没有返回有效结果");
        }
    }

    private static String requireText(ChatResponse response) {
        String text = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("模型没有返回可展示文本");
        }
        return text;
    }

    /** 只用于同步路径，每个 query 创建一份，不能注册成共享单例。 */
    private static final class ModelRounds implements CallAdvisor {
        private final int max;
        private int modelCalls;
        private int reportedCalls;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;

        private ModelRounds(int max) { this.max = max; }

        private void beforeCall() {
            if (modelCalls >= max) { throw new ModelCallBudgetExceededException(); }
            modelCalls++;
        }

        private void record(ChatResponse response) {
            Usage usage = response == null ? null : response.getMetadata().getUsage();
            if (usage == null || usage instanceof EmptyUsage || usage.getPromptTokens() == null
                    || usage.getCompletionTokens() == null || usage.getTotalTokens() == null) { return; }
            reportedCalls++;
            promptTokens += usage.getPromptTokens();
            completionTokens += usage.getCompletionTokens();
            totalTokens += usage.getTotalTokens();
        }

        private UsageSummary summary() {
            return new UsageSummary(modelCalls, reportedCalls, reportedCalls == modelCalls,
                    reportedCalls == 0 ? null : promptTokens,
                    reportedCalls == 0 ? null : completionTokens,
                    reportedCalls == 0 ? null : totalTokens);
        }

        @Override public String getName() { return "只读工具请求预算与用量"; }
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 400; }
        @Override public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            beforeCall();
            ChatClientResponse response = chain.nextCall(request);
            record(response.chatResponse());
            return response;
        }
    }

    public enum Mode { AUTO, MANUAL, PLAN_ONLY, DIRECT }
    public enum Status { COMPLETED, DIRECT, EXECUTION_SKIPPED, TOOL_CALL_LIMIT, MODEL_CALL_LIMIT }
    public record QueryRequest(String question, Mode mode) { }
    public record QueryResult(Status status, String answer, String finishReason, List<String> pendingTools,
            UsageSummary usage) { }
    /** Token 数只累计已报告的轮次，complete=false 时不得称为完整成本。 */
    public record UsageSummary(int modelCalls, int reportedCalls, boolean complete,
            Long promptTokens, Long completionTokens, Long totalTokens) { }
    public static final class InvalidToolRequestException extends IllegalArgumentException {
        public InvalidToolRequestException(String message) { super(message); }
    }
    public static final class ToolQueryFailureException extends RuntimeException {
        public ToolQueryFailureException(Throwable cause) { super("库存问答执行失败", cause); }
    }
    private static final class ModelCallBudgetExceededException extends RuntimeException { }
}
