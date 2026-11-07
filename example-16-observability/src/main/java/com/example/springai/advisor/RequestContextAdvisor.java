package com.example.springai.advisor;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;

/**
 * 一个最小的同步/流式 Advisor，用于展示 Advisor observation 和请求上下文传播。
 */
public final class RequestContextAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String CONTEXT_KEY = "demo.observability.request";

    @Override
    public String getName() {
        return "request-context";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest updated = request.mutate().context(CONTEXT_KEY, "offline").build();
        return chain.nextCall(updated);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ChatClientRequest updated = request.mutate().context(CONTEXT_KEY, "offline").build();
        return chain.nextStream(updated);
    }

}
