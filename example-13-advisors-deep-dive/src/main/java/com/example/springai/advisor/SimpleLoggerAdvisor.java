package com.example.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 官方文档中的观察型 Advisor：只记录请求和响应，不改变数据。
 */
public final class SimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SimpleLoggerAdvisor.class);

    private final int order;

    public SimpleLoggerAdvisor() {
        this(0);
    }

    public SimpleLoggerAdvisor(int order) {
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
        logRequest(request);
        ChatClientResponse response = chain.nextCall(request);
        logResponse(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        logRequest(request);
        Flux<ChatClientResponse> responses = chain.nextStream(request);

        // 聚合器只用于观察完整响应；回调不能替换流中的响应。
        return new ChatClientMessageAggregator().aggregateChatClientResponse(responses, this::logResponse);
    }

    private void logRequest(ChatClientRequest request) {
        String userText = request.prompt().getUserMessage().getText();
        logger.info("Advisor 请求：消息数={}，用户消息长度={}",
                request.prompt().getInstructions().size(), userText == null ? 0 : userText.length());
        logger.debug("Advisor 请求上下文键：{}", request.context().keySet());
    }

    private void logResponse(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            logger.info("Advisor 响应：空响应");
            return;
        }
        int generationCount = response.chatResponse().getResults().size();
        String responseText = generationCount == 0
                ? null
                : response.chatResponse().getResults().get(0).getOutput().getText();
        logger.info("Advisor 响应：Generation 数={}，首个文本长度={}",
                generationCount,
                responseText == null ? 0 : responseText.length());
        logger.debug("Advisor 响应上下文键：{}", response.context().keySet());
    }

}
