package com.example.springai.service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.springai.advisor.ContentSafetyAdvisor;
import com.example.springai.advisor.PerformanceMonitorAdvisor;
import com.example.springai.advisor.ReReadingAdvisor;
import com.example.springai.advisor.ResponseLabelAdvisor;
import com.example.springai.advisor.SimpleLoggerAdvisor;
import com.example.springai.support.AdvisorOperationException;
import com.example.springai.support.FeatureUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 创建并执行教程中的 Advisor Pipeline。ChatModel 是可选依赖，因此应用默认可以离线启动。
 */
@Service
public class AdvisorPipelineService {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final ObjectProvider<ChatModel> chatModelProvider;

    public AdvisorPipelineService(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    public ModelStatus status() {
        ChatModel chatModel = this.chatModelProvider.getIfAvailable();
        return new ModelStatus(
                chatModel != null,
                chatModel == null ? null : chatModel.getClass().getSimpleName(),
                advisorInfo());
    }

    public ChatReply call(String message) {
        String normalized = validateMessage(message);
        ChatModel chatModel = requireChatModel();
        try {
            String response = buildChatClient(chatModel)
                .prompt()
                .user(normalized)
                .call()
                .content();
            if (response == null || response.isBlank()) {
                throw new AdvisorOperationException("模型返回空响应，请检查供应商响应格式");
            }
            return new ChatReply(normalized, response, chatModel.getClass().getSimpleName());
        }
        catch (AdvisorOperationException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new AdvisorOperationException(
                    "Advisor Pipeline 调用失败，请检查模型配置、网络连接和 Advisor 日志", exception);
        }
    }

    /** 使用同一套 Advisor 和固定本地模型演示控制流，不读取外部模型 Bean。 */
    public PreviewReply preview(String message) {
        String normalized = validateMessage(message);
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel fixedModel = prompt -> {
            modelCalls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("固定演示回答，未访问外部模型"))));
        };
        String answer = buildChatClient(fixedModel).prompt().user(normalized).call().content();
        return new PreviewReply(answer, modelCalls.get(), false, advisorInfo());
    }

    public Flux<String> stream(String message) {
        String normalized = validateMessage(message);
        ChatModel chatModel = requireChatModel();
        return buildChatClient(chatModel)
            .prompt()
            .user(normalized)
            .stream()
            .content()
            .filter(chunk -> !chunk.isEmpty())
            .switchIfEmpty(Flux.error(new AdvisorOperationException(
                    "模型未返回任何流式内容，请检查供应商响应格式")))
            .onErrorMap(error -> error instanceof AdvisorOperationException
                    ? error
                    : new AdvisorOperationException(
                            "Advisor Pipeline 流式调用失败，请检查模型配置、网络连接和 Advisor 日志",
                            error));
    }

    private ChatClient buildChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(buildAdvisors())
            .build();
    }

    private List<Advisor> buildAdvisors() {
        return List.of(
                new PerformanceMonitorAdvisor(),
                new SimpleLoggerAdvisor(Ordered.HIGHEST_PRECEDENCE + 150),
                // 放在 ToolCallingAdvisor（+300）之前，避免工具循环的每一轮重复增强问题。
                new ReReadingAdvisor().withOrder(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 25),
                new ContentSafetyAdvisor(
                        List.of("违法操作", "泄露密钥"),
                        "请求包含敏感内容，已停止调用模型。",
                        Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50),
                new ResponseLabelAdvisor(
                        "[Advisor 已处理] ",
                        Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 75));
    }

    private List<AdvisorInfo> advisorInfo() {
        List<AdvisorInfo> customAdvisors = buildAdvisors().stream()
            .map(advisor -> new AdvisorInfo(advisor.getName(), advisor.getOrder()))
            .toList();
        return java.util.stream.Stream.concat(
                customAdvisors.stream(),
                java.util.stream.Stream.of(new AdvisorInfo(
                        "Tool Calling Advisor（ChatClient 自动注册）",
                        ToolCallingAdvisor.DEFAULT_ORDER)))
            .sorted(Comparator.comparingInt(AdvisorInfo::order))
            .toList();
    }

    private ChatModel requireChatModel() {
        ChatModel chatModel = this.chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new FeatureUnavailableException(
                    "尚未启用 ChatModel。请设置 SPRING_AI_CHAT_MODEL=anthropic 和 MINIMAX_API_KEY 后重启应用。");
        }
        return chatModel;
    }

    private String validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        String normalized = message.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message 长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }
        return normalized;
    }

    public record AdvisorInfo(String name, int order) {
    }

    public record ModelStatus(boolean enabled, String modelType, List<AdvisorInfo> advisors) {
    }

    public record ChatReply(String request, String response, String modelType) {
    }

    /** configuredAdvisors 是配置顺序，不等于阻断后每个 Advisor 都已执行。 */
    public record PreviewReply(String response, int modelCalls, boolean externalModelCalled,
            List<AdvisorInfo> configuredAdvisors) { }

}
