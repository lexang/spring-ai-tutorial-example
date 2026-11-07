package com.example.springai.advisor;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

/**
 * 统计一次 Call 或完整 Stream 的耗时。开始时间是调用级局部变量，不放在 Advisor 实例字段中。
 */
public final class PerformanceMonitorAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitorAdvisor.class);

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        try {
            return chain.nextCall(request);
        }
        finally {
            logDuration("同步调用", started);
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return Flux.defer(() -> {
            long started = System.nanoTime();
            return new ChatClientMessageAggregator()
                .aggregateChatClientResponse(chain.nextStream(request), response -> {
                    logger.debug("流式响应已聚合，Generation 数：{}", response.chatResponse() == null
                            ? 0 : response.chatResponse().getResults().size());
                })
                .doFinally(signal -> logDuration("流式调用（" + signal + "）", started));
        });
    }

    private void logDuration(String operation, long started) {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        logger.info("{}耗时：{} ms", operation, elapsed);
    }

}
