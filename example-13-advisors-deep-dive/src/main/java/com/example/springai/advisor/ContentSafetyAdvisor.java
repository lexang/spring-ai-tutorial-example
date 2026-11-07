package com.example.springai.advisor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

/**
 * 自定义请求阻断 Advisor。生产代码也可以直接使用官方 {@code SafeGuardAdvisor}。
 */
public final class ContentSafetyAdvisor implements CallAdvisor, StreamAdvisor {

    private final List<String> sensitiveWords;

    private final String failureResponse;

    private final int order;

    public ContentSafetyAdvisor(List<String> sensitiveWords) {
        this(sensitiveWords, "请求包含敏感内容，已停止调用模型。", Ordered.LOWEST_PRECEDENCE);
    }

    public ContentSafetyAdvisor(List<String> sensitiveWords, String failureResponse, int order) {
        Assert.notNull(sensitiveWords, "敏感词列表不能为空");
        Assert.hasText(failureResponse, "阻断响应不能为空");
        this.sensitiveWords = List.copyOf(sensitiveWords);
        this.failureResponse = failureResponse;
        this.order = order;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return containsSensitiveWord(request) ? blockedResponse(request) : chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return containsSensitiveWord(request) ? Flux.just(blockedResponse(request)) : chain.nextStream(request);
    }

    private boolean containsSensitiveWord(ChatClientRequest request) {
        String content = request.prompt().getContents().toLowerCase(Locale.ROOT);
        return this.sensitiveWords.stream()
            .map(word -> word.toLowerCase(Locale.ROOT))
            .anyMatch(content::contains);
    }

    private ChatClientResponse blockedResponse(ChatClientRequest request) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(this.failureResponse))));
        return ChatClientResponse.builder()
            .chatResponse(chatResponse)
            .context(Map.copyOf(request.context()))
            .build();
    }

}
