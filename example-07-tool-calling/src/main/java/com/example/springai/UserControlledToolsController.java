package com.example.springai;

import com.example.springai.exception.MathTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户控制的工具执行示例控制器
 *
 * 演示如何手动控制工具的执行流程
 * - 使用 ChatClient 的 ToolCallingAdvisor 自动执行工具
 * - 手动执行工具调用
 * - 完全控制工具执行的生命周期
 */
@RestController
@RequestMapping("/api/user-controlled")
public class UserControlledToolsController {

    private static final Logger log = LoggerFactory.getLogger(UserControlledToolsController.class);

    private static final int MAX_TOOL_ROUNDS = 10;

    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final CalculatorTools calculatorTools;
    private final DateTimeTools dateTimeTools;
    private final ToolCallingManager toolCallingManager;

    public UserControlledToolsController(
            ChatModel chatModel,
            ChatClient.Builder chatClientBuilder,
            CalculatorTools calculatorTools,
            DateTimeTools dateTimeTools,
            ToolCallingManager toolCallingManager) {

        this.chatModel = chatModel;
        this.chatClient = chatClientBuilder.clone().build();
        this.calculatorTools = calculatorTools;
        this.dateTimeTools = dateTimeTools;
        this.toolCallingManager = toolCallingManager;

        log.info("UserControlledToolsController 初始化完成");
    }

    /**
     * 示例 1: 自动执行 vs 手动执行对比
     *
     * 演示默认自动执行和手动执行的区别
     */
    @GetMapping("/compare")
    public String compare(@RequestParam String question) {
        question = requireInput(question, "问题");
        log.info("收到对比请求: {}", question);

        StringBuilder result = new StringBuilder();

        // ===== 自动执行模式 =====
        result.append("【自动执行模式】\n");
        result.append("Spring AI 自动处理工具调用\n\n");

        String autoResponse = this.chatClient.prompt(question)
                .tools(calculatorTools)
                .call()
                .content();

        result.append("结果: ").append(requireModelText(autoResponse)).append("\n\n");

        // ===== 手动执行模式 =====
        result.append("【手动执行模式】\n");
        result.append("用户完全控制工具执行流程\n\n");

        String manualResult = executeManually(question, calculatorTools);
        result.append("结果: ").append(manualResult).append("\n");

        return result.toString();
    }

    /**
     * 示例 2: 基础手动执行
     *
     * 演示最基本的手动工具执行流程
     */
    @GetMapping("/basic")
    public String basic(@RequestParam String question) {
        question = requireInput(question, "问题");
        log.info("收到基础手动执行请求: {}", question);

        return executeManually(question, calculatorTools);
    }

    /**
     * 示例 3: 多工具手动执行
     *
     * 演示使用多个工具的手动执行流程
     */
    @GetMapping("/multi-tool")
    public String multiTool(@RequestParam String question) {
        question = requireInput(question, "问题");
        log.info("收到多工具手动执行请求: {}", question);

        // 创建多个工具回调
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        toolCallbacks.addAll(List.of(ToolCallbacks.from(calculatorTools)));
        toolCallbacks.addAll(List.of(ToolCallbacks.from(dateTimeTools)));

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .build();

        Prompt prompt = new Prompt(question, chatOptions);
        ChatResponse response = chatModel.call(prompt);

        // 记录执行过程
        StringBuilder executionLog = new StringBuilder();
        executionLog.append("=== 工具执行追踪 ===\n\n");

        int iteration = 0;
        String finalAnswer = null;

        // 循环处理工具调用
        while (response.hasToolCalls()) {
            iteration = nextRound(iteration);
            executionLog.append(String.format("【第 %d 轮】\n", iteration));

            // 记录工具调用信息
            String toolNames = response.getResult().getOutput().getToolCalls().stream()
                    .map(toolCall -> toolCall.name())
                    .collect(Collectors.joining(", "));
            executionLog.append("模型请求调用工具: ").append(toolNames).append("\n");

            // 执行工具调用
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);

            if (result.returnDirect()) {
                finalAnswer = directResultText(result);
                executionLog.append("工具结果按 returnDirect 直接返回\n\n");
                break;
            }

            // 使用工具结果创建新的 Prompt
            prompt = new Prompt(result.conversationHistory(), chatOptions);

            // 再次调用模型
            response = chatModel.call(prompt);
        }

        if (finalAnswer == null) {
            finalAnswer = responseText(response);
        }

        executionLog.append("=== 最终结果 ===\n");
        executionLog.append(finalAnswer);

        return executionLog.toString();
    }

    /**
     * 示例 4: 工作流追踪
     *
     * 演示如何追踪和记录整个工具执行工作流
     */
    @GetMapping("/workflow")
    public String workflow(@RequestParam String task) {
        task = requireInput(task, "任务");
        log.info("收到工作流请求: {}", task);

        WorkflowTracker tracker = new WorkflowTracker();

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(calculatorTools))
                .build();

        Prompt prompt = new Prompt(task, chatOptions);
        ChatResponse response = chatModel.call(prompt);

        tracker.recordStep("初始请求", task);
        int rounds = 0;
        String finalAnswer = null;

        // 处理工具调用
        while (response.hasToolCalls()) {
            rounds = nextRound(rounds);
            String toolNames = response.getResult().getOutput().getToolCalls().stream()
                    .map(toolCall -> toolCall.name())
                    .collect(Collectors.joining(", "));
            tracker.recordStep("模型请求工具调用", "第 " + rounds + " 轮: " + toolNames);

            // 执行工具
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            tracker.recordStep("工具执行完成",
                    String.format("执行了 %d 个工具", response.getResult().getOutput().getToolCalls().size()));

            if (result.returnDirect()) {
                finalAnswer = directResultText(result);
                tracker.recordStep("工具直接返回", finalAnswer);
                break;
            }

            // 继续对话
            prompt = new Prompt(result.conversationHistory(), chatOptions);
            response = chatModel.call(prompt);
        }

        if (finalAnswer == null) {
            finalAnswer = responseText(response);
            tracker.recordStep("获得最终答案", finalAnswer);
        }

        return tracker.getReport();
    }

    /**
     * 示例 5: 条件执行
     *
     * 演示根据条件决定是否执行工具
     */
    @GetMapping("/conditional")
    public String conditional(
            @RequestParam String question,
            @RequestParam(defaultValue = "true") boolean allowToolExecution) {

        question = requireInput(question, "问题");
        log.info("收到条件执行请求: {}, 允许工具执行: {}", question, allowToolExecution);

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(calculatorTools))
                .build();

        Prompt prompt = new Prompt(question, chatOptions);
        ChatResponse response = chatModel.call(prompt);

        if (!allowToolExecution) {
            if (response.hasToolCalls()) {
                String toolNames = response.getResult().getOutput().getToolCalls().stream()
                        .map(toolCall -> toolCall.name())
                        .collect(Collectors.joining(", "));
                return "⚠️ 工具执行被禁止\n\n模型请求调用: " + toolNames;
            }
            return "⚠️ 工具执行被禁止\n\n模型响应: " + responseText(response);
        }

        // 执行工具
        int rounds = 0;
        while (response.hasToolCalls()) {
            rounds = nextRound(rounds);
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            if (result.returnDirect()) {
                return "✅ 工具执行已完成\n\n直接返回结果: " + directResultText(result);
            }
            prompt = new Prompt(result.conversationHistory(), chatOptions);
            response = chatModel.call(prompt);
        }

        return "✅ 工具执行已完成\n\n最终结果: " +
                responseText(response);
    }

    /**
     * 示例 6: ChatMemory
     *
     */
    @GetMapping("/chatMemory")
    public String chatMemory() {

        log.info("收到 ChatMemory 手动工具循环请求");

        ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        String conversationId = UUID.randomUUID().toString();

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(new MathTools()))
                .build();
        SystemMessage systemMessage = new SystemMessage("你是一个数学助手，请使用工具完成计算。");
        UserMessage firstUserMessage = new UserMessage("6 乘以 8 等于多少？");

        List<Message> turnMessages = new ArrayList<>();
        turnMessages.add(systemMessage);
        turnMessages.addAll(chatMemory.get(conversationId));
        turnMessages.add(firstUserMessage);

        Prompt prompt = new Prompt(turnMessages, chatOptions);
        ChatResponse response = chatModel.call(prompt);
        AssistantMessage finalAssistant = null;
        int rounds = 0;

        // 当前轮次保留完整的工具调用历史，但默认只把最终用户/助手交换写入长期记忆。
        while (response.hasToolCalls()) {
            rounds = nextRound(rounds);
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            if (result.returnDirect()) {
                finalAssistant = new AssistantMessage(directResultText(result));
                break;
            }
            prompt = new Prompt(result.conversationHistory(), chatOptions);
            response = chatModel.call(prompt);
        }

        if (finalAssistant == null) {
            finalAssistant = response.getResult().getOutput();
            requireModelText(finalAssistant.getText());
        }

        chatMemory.add(conversationId, firstUserMessage);
        chatMemory.add(conversationId, finalAssistant);

        UserMessage nextUserMessage = new UserMessage("我刚才问了你什么？");
        List<Message> nextMessages = new ArrayList<>();
        nextMessages.add(systemMessage);
        nextMessages.addAll(chatMemory.get(conversationId));
        nextMessages.add(nextUserMessage);

        ChatResponse nextResponse = chatModel.call(new Prompt(nextMessages, chatOptions));
        String answer = responseText(nextResponse);
        chatMemory.add(conversationId, nextUserMessage);
        chatMemory.add(conversationId, nextResponse.getResult().getOutput());

        return "最终回答: " + answer;
    }

    // ========== 辅助方法 ==========

    /**
     * 手动执行工具调用的通用方法
     */
    private String executeManually(String question, Object... tools) {
        question = requireInput(question, "问题");
        if (tools == null || tools.length == 0) {
            throw new IllegalArgumentException("至少需要提供一个工具");
        }

        // 构建工具回调列表
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        for (Object tool : tools) {
            toolCallbacks.addAll(List.of(ToolCallbacks.from(tool)));
        }

        // 配置选项：禁用自动执行
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .build();

        // 创建初始 Prompt
        Prompt prompt = new Prompt(question, chatOptions);

        // 第一次调用模型
        log.info("调用模型...");
        ChatResponse response = chatModel.call(prompt);

        // 循环处理工具调用
        int rounds = 0;
        while (response.hasToolCalls()) {
            rounds = nextRound(rounds);
            log.info("检测到工具调用 (第 {} 轮)", rounds);

            // 手动执行工具调用
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);

            if (result.returnDirect()) {
                log.info("工具设置了 returnDirect，直接返回执行结果");
                return directResultText(result);
            }

            // 使用工具结果创建新的 Prompt
            prompt = new Prompt(result.conversationHistory(), chatOptions);

            // 再次调用模型
            log.info("再次调用模型...");
            response = chatModel.call(prompt);
        }

        log.info("工具执行完成，总共 {} 轮", rounds);

        // 返回最终结果
        return responseText(response);
    }

    private static int nextRound(int currentRound) {
        int nextRound = currentRound + 1;
        if (nextRound > MAX_TOOL_ROUNDS) {
            throw new IllegalStateException("工具循环超过最大轮数 " + MAX_TOOL_ROUNDS);
        }
        return nextRound;
    }

    private static String directResultText(ToolExecutionResult result) {
        String text = ToolExecutionResult.buildGenerations(result).stream()
                .map(generation -> generation.getOutput().getText())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("工具设置了 returnDirect，但没有返回可展示的文本");
        }
        return text;
    }

    private static String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型未返回有效响应");
        }
        return requireModelText(response.getResult().getOutput().getText());
    }

    private static String requireModelText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("模型未返回可展示的文本");
        }
        return text;
    }

    private static String requireInput(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }



    // ========== 内部类 ==========

    /**
     * 工作流追踪器
     */
    private static class WorkflowTracker {
        private final List<WorkflowStep> steps = new ArrayList<>();

        public void recordStep(String action, String details) {
            steps.add(new WorkflowStep(action, details));
        }

        public String getReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== 工作流执行报告 ===\n\n");

            for (int i = 0; i < steps.size(); i++) {
                WorkflowStep step = steps.get(i);
                report.append(String.format("步骤 %d: %s\n", i + 1, step.action()));
                report.append(String.format("详情: %s\n\n", step.details()));
            }

            return report.toString();
        }
    }

    /**
     * 工作流步骤
     */
    private record WorkflowStep(String action, String details) {
    }
}
