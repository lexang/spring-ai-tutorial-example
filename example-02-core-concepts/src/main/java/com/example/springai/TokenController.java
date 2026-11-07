package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Token 示例控制器。
 * 演示 Token 使用量、演示费率和输出限制。
 */
@RestController
@RequestMapping("/token")
public class TokenController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    // 仅用于演示公式，不是实时供应商价格。
    private static final double DEMO_INPUT_COST_PER_MILLION = 0.150;
    private static final double DEMO_OUTPUT_COST_PER_MILLION = 0.600;
    private static final double DEMO_USD_TO_CNY = 7.2;

    private final ChatClient chatClient;

    public TokenController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /** 本地估算文本 Token，不访问模型 API。 */
    @GetMapping("/estimate")
    public Map<String, Object> estimateTokens(@RequestParam String text) {
        requireText(text, "text");
        return Map.of(
                "characterCount", text.length(),
                "estimatedTokens", new JTokkitTokenCountEstimator().estimate(text),
                "note", "这是本地 tokenizer 估算，不包含完整请求开销，也不是供应商账单"
        );
    }

    /**
     * 示例 1：获取 Token 使用情况。
     * 示例：http://localhost:8080/token/usage?message=介绍一下Spring AI
     */
    @GetMapping("/usage")
    public Map<String, Object> getTokenUsage(@RequestParam String message) {
        requireText(message, "message");
        log.info("正在分析消息的 Token 使用量，字符数：{}", message.length());

        ChatResponse chatResponse = requireResponse(chatClient.prompt()
                .user(message)
                .call()
                .chatResponse());
        Usage usage = requireUsage(chatResponse);
        String content = responseText(chatResponse);

        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("response", content);
        result.put("tokenUsage", usageMap(usage));

        double cost = calculateCost(usageValue(usage.getPromptTokens()), usageValue(usage.getCompletionTokens()));
        result.put("cost", costMap(cost));
        result.put("pricingNote", "成本使用演示费率计算，不是供应商账单");

        log.info("Token 使用量 - 输入：{}，输出：{}，总计：{}",
                usageValue(usage.getPromptTokens()),
                usageValue(usage.getCompletionTokens()),
                usageValue(usage.getTotalTokens()));
        return result;
    }

    /**
     * 示例 2：比较不同长度输入的 Token 使用。
     */
    @GetMapping("/compare")
    public Map<String, Object> compareTokenUsage() {
        String[] messages = {
                "你好",
                "请介绍一下 Spring AI",
                "请详细介绍 Spring AI 框架，包括它的核心功能、使用场景和优势"
        };

        Map<String, Object> results = new HashMap<>();
        results.put("title", "Token 使用量对比");
        results.put("note", "实际数量和输出长度取决于模型及供应商");

        for (int i = 0; i < messages.length; i++) {
            String message = messages[i];
            ChatResponse response = requireResponse(chatClient.prompt()
                    .user(message)
                    .call()
                    .chatResponse());
            Usage usage = requireUsage(response);
            results.put("case" + (i + 1), Map.of(
                    "message", message,
                    "messageLength", message.length(),
                    "promptTokens", usageValue(usage.getPromptTokens()),
                    "completionTokens", usageValue(usage.getCompletionTokens()),
                    "totalTokens", usageValue(usage.getTotalTokens()),
                    "cost", String.format("$%.6f", calculateCost(
                            usageValue(usage.getPromptTokens()), usageValue(usage.getCompletionTokens())))
            ));
        }
        return results;
    }

    /**
     * 示例 3：限制输出 Token 数。
     * <p>非推理模型使用 maxTokens；推理模型使用 maxCompletionTokens，二者不能同时设置。</p>
     */
    @GetMapping("/limit")
    public Map<String, Object> limitTokens(
            @RequestParam String message,
            @RequestParam(defaultValue = "100") Integer maxTokens,
            @RequestParam(defaultValue = "false") boolean reasoningModel) {

        requireText(message, "message");
        if (maxTokens == null || maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens 必须大于 0");
        }

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        String optionName;
        if (reasoningModel) {
            optionsBuilder.maxCompletionTokens(maxTokens);
            optionName = "maxCompletionTokens";
        }
        else {
            optionsBuilder.maxTokens(maxTokens);
            optionName = "maxTokens";
        }

        log.info("请求输出上限：{}={}（推理模型：{}）", optionName, maxTokens, reasoningModel);
        ChatResponse response = requireResponse(chatClient.prompt()
                .user(message)
                .options(optionsBuilder)
                .call()
                .chatResponse());
        Usage usage = requireUsage(response);
        String finishReason = finishReason(response);

        return Map.of(
                "message", message,
                "limitOption", optionName,
                "maxTokens", maxTokens,
                "response", responseText(response),
                "actualTokens", usageValue(usage.getCompletionTokens()),
                "finishReason", finishReason == null ? "unknown" : finishReason,
                "limited", isLengthFinishReason(finishReason)
        );
    }

    /**
     * 示例 4：成本估算工具。所有价格均为演示常量。
     */
    @GetMapping("/cost-calculator")
    public Map<String, Object> costCalculator(
            @RequestParam int calls,
            @RequestParam int avgPrompt,
            @RequestParam int avgCompletion) {

        requireNonNegative("calls", calls);
        requireNonNegative("avgPrompt", avgPrompt);
        requireNonNegative("avgCompletion", avgCompletion);

        double costPerCall = calculateCost(avgPrompt, avgCompletion);
        double totalCost = costPerCall * calls;

        return Map.of(
                "scenario", "成本估算",
                "parameters", Map.of(
                        "totalCalls", calls,
                        "avgPromptTokens", avgPrompt,
                        "avgCompletionTokens", avgCompletion
                ),
                "results", Map.of(
                        "costPerCall", costMap(costPerCall),
                        "totalCost", Map.of(
                                "usd", String.format("$%.2f", totalCost),
                                "cny", String.format("%.2f元", totalCost * DEMO_USD_TO_CNY)
                        )
                ),
                "pricing", Map.of(
                        "type", "demo",
                        "inputPricePerM", "$" + DEMO_INPUT_COST_PER_MILLION + " / 1M tokens",
                        "outputPricePerM", "$" + DEMO_OUTPUT_COST_PER_MILLION + " / 1M tokens",
                        "note", "请替换为供应商当前费率后再用于预算"
                )
        );
    }

    /**
     * 示例 5：温度参数对 Token 的影响。
     */
    @GetMapping("/temperature")
    public Map<String, Object> temperatureEffect(
            @RequestParam String message,
            @RequestParam(defaultValue = "0.7") Double temp) {

        requireText(message, "message");
        if (temp == null || !Double.isFinite(temp) || temp < 0 || temp > 2) {
            throw new IllegalArgumentException("temp 必须是 0 到 2 之间的有限数值");
        }

        ChatResponse response = requireResponse(chatClient.prompt()
                .user(message)
                .options(OpenAiChatOptions.builder().temperature(temp))
                .call()
                .chatResponse());
        Usage usage = requireUsage(response);

        return Map.of(
                "message", message,
                "temperature", temp,
                "response", responseText(response),
                "tokens", usageMap(usage),
                "note", "温度影响输出分布，但不直接规定 Token 数量"
        );
    }

    /**
     * 按“美元 / 100 万 Token”的演示费率计算成本。
     */
    static double calculateCost(long promptTokens, long completionTokens) {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("Token 数不能为负数");
        }
        double inputCost = (promptTokens * DEMO_INPUT_COST_PER_MILLION) / 1_000_000;
        double outputCost = (completionTokens * DEMO_OUTPUT_COST_PER_MILLION) / 1_000_000;
        return inputCost + outputCost;
    }

    private ChatResponse requireResponse(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("模型未返回有效响应");
        }
        return response;
    }

    private Usage requireUsage(ChatResponse response) {
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata == null || metadata.getUsage() == null || metadata.getUsage() instanceof EmptyUsage) {
            throw new IllegalStateException("模型响应未提供 Token 使用量");
        }
        return metadata.getUsage();
    }

    private String responseText(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String finishReason(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    private boolean isLengthFinishReason(String finishReason) {
        return "length".equalsIgnoreCase(finishReason) || "max_tokens".equalsIgnoreCase(finishReason);
    }

    private Map<String, Object> usageMap(Usage usage) {
        return Map.of(
                "promptTokens", usageValue(usage.getPromptTokens()),
                "completionTokens", usageValue(usage.getCompletionTokens()),
                "totalTokens", usageValue(usage.getTotalTokens())
        );
    }

    private Map<String, String> costMap(double cost) {
        return Map.of(
                "usd", String.format("$%.6f", cost),
                "cny", String.format("%.4f元", cost * DEMO_USD_TO_CNY)
        );
    }

    private int usageValue(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " 不能为空");
        }
    }

    private void requireNonNegative(String parameterName, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(parameterName + " 不能为负数");
        }
    }

    /** 主页 - API 列表。 */
    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "title", "Token 示例 API",
                "pricing", Map.of(
                        "type", "demo",
                        "input", "$" + DEMO_INPUT_COST_PER_MILLION + " / 1M tokens",
                        "output", "$" + DEMO_OUTPUT_COST_PER_MILLION + " / 1M tokens",
                        "note", "不是实时供应商价格"
                ),
                "endpoints", Map.of(
                        "/token/usage", "查看 Token 使用情况",
                        "/token/compare", "比较不同输入的 Token 使用",
                        "/token/limit", "限制输出 Token 数",
                        "/token/cost-calculator", "成本估算工具",
                        "/token/temperature", "温度参数效果"
                )
        );
    }
}
