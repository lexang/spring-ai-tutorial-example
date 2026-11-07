package com.example.springai;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** 根据明确任务选择客户端，不声称进行了模型质量或成本最优选择。 */
@Service
public final class TaskRoutingService {
    private final Map<String, ChatClient> clients;

    public TaskRoutingService(@Qualifier("fast") ChatClient fast,
            @Qualifier("standard") ChatClient standard,
            @Qualifier("creative") ChatClient creative,
            @Qualifier("minimax") ChatClient miniMax) {
        this.clients = Map.of("fast", fast, "standard", standard, "creative", creative, "minimax", miniMax);
    }

    public RoutePlan plan(Task task) {
        if (task == null) throw new IllegalArgumentException("task 不能为空");
        return switch (task) {
            case SHORT_ANSWER -> new RoutePlan(task, "fast", "openai", "使用简洁提示和较低输出上限");
            case WRITE -> new RoutePlan(task, "creative", "openai", "使用写作提示与对应选项");
            case ANALYZE -> new RoutePlan(task, "minimax", "anthropic-compatible", "演示另一供应商的独立连接");
            case GENERAL -> new RoutePlan(task, "standard", "openai", "使用默认通用客户端");
        };
    }

    public RoutedReply chat(Task task, String message) {
        RoutePlan route = plan(task);
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message 不能为空");
        if (message.length() > 8000) throw new IllegalArgumentException("message 不能超过 8000 个字符");
        org.springframework.ai.chat.model.ChatResponse response;
        try {
            response = this.clients.get(route.clientId()).prompt().user(message).call().chatResponse();
        } catch (RuntimeException exception) {
            throw new ModelCallFailureException("模型调用失败", exception);
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput().getText() == null
                || response.getResult().getOutput().getText().isBlank()) {
            throw new ModelCallFailureException("模型未返回非空文本", null);
        }
        Usage usage = response.getMetadata().getUsage();
        boolean reported = usage != null && !(usage instanceof EmptyUsage);
        return new RoutedReply(route, response.getResult().getOutput().getText(),
                response.getMetadata().getModel(), reported, reported ? usage.getTotalTokens() : null);
    }

    public enum Task { SHORT_ANSWER, GENERAL, WRITE, ANALYZE }
    public record RoutePlan(Task task, String clientId, String provider, String reason) { }
    public record RoutedReply(RoutePlan route, String content, String responseModel,
            boolean usageReported, Integer totalTokens) { }
}
