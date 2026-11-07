/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.springai;

import java.util.concurrent.atomic.LongAdder;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;

/**
 * 同步调用的用量与耗时统计，独立于日志级别。
 * 置于工具循环外，每次应用请求统计一次；流式指标由标准 observation 记录。
 */
public final class UsageTimingAdvisor implements CallAdvisor {

    private final LongAdder calls = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder tokens = new LongAdder();
    private final LongAdder elapsedNanos = new LongAdder();

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        try {
            ChatClientResponse response = chain.nextCall(request);
            Usage usage = response.chatResponse() == null ? null : response.chatResponse().getMetadata().getUsage();
            if (usage != null && usage.getTotalTokens() != null) {
                this.tokens.add(usage.getTotalTokens());
            }
            return response;
        } catch (RuntimeException exception) {
            this.failures.increment();
            throw exception;
        } finally {
            this.calls.increment();
            this.elapsedNanos.add(System.nanoTime() - started);
        }
    }

    @Override
    public String getName() {
        return "UsageTimingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    /** 并发计数器的即时快照，用于演示监控，不作为结算账本。 */
    public Snapshot snapshot() {
        return new Snapshot(this.calls.sum(), this.failures.sum(), this.tokens.sum(), this.elapsedNanos.sum());
    }

    public record Snapshot(long calls, long failures, long reportedTokens, long elapsedNanos) {
    }
}
