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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.springai.advisor.PerformanceMonitorAdvisor;
import com.example.springai.advisor.ResponseLabelAdvisor;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvisorBoundaryTest {

    @Test
    void initialContextAlreadyContainsDefaultAndRequestParameters() {
        AtomicReference<Map<String, Object>> observed = new AtomicReference<>();
        BaseAdvisor inspector = new BaseAdvisor() {
            @Override public String getName() { return "InitialContextInspector"; }
            @Override public int getOrder() { return Integer.MIN_VALUE + 10; }
            @Override public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                observed.set(Map.copyOf(request.context()));
                return request;
            }
            @Override public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) { return response; }
        };
        ChatClient.builder(prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("完成")))))
                .defaultAdvisors(a -> a.advisors(inspector).param("default", "已设置"))
                .build().prompt().user("测试").advisors(a -> a.param("request", "也已设置")).call().content();
        assertThat(observed.get()).containsEntry("default", "已设置").containsEntry("request", "也已设置");
    }

    @Test
    void responseLabelsLeaveToolCallsAndEmptyTextUnchanged() {
        AssistantMessage tool = AssistantMessage.builder().toolCalls(List.of(
                new AssistantMessage.ToolCall("id", "function", "lookup", "{}"))).build();
        AssistantMessage empty = new AssistantMessage("");
        AssistantMessage specialized = new AssistantMessage("供应商专有消息") { };
        ChatClientResponse input = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(tool), new Generation(empty), new Generation(specialized))),
                Map.of("trace", "test"));
        ChatClientResponse output = new ResponseLabelAdvisor("[标签]", 0).after(input, mock(AdvisorChain.class));
        assertThat(output.chatResponse().getResults().get(0).getOutput()).isSameAs(tool);
        assertThat(output.chatResponse().getResults().get(1).getOutput()).isSameAs(empty);
        assertThat(output.chatResponse().getResults().get(2).getOutput()).isSameAs(specialized);
        assertThat(output.context()).containsEntry("trace", "test");
    }

    @Test
    void performanceAdvisorRecordsCancellationOnceWithoutLoggingResponseBody() {
        Logger logger = (Logger) LoggerFactory.getLogger(PerformanceMonitorAdvisor.class);
        var previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            var response = new ChatClientResponse(new ChatResponse(
                    List.of(new Generation(new AssistantMessage("不应写入日志的正文")))), Map.of());
            StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
            when(chain.nextStream(any())).thenReturn(Flux.just(response));
            StepVerifier.create(new PerformanceMonitorAdvisor().adviseStream(
                    new ChatClientRequest(new Prompt("完整响应"), Map.of()), chain))
                    .expectNext(response).verifyComplete();
            when(chain.nextStream(any())).thenReturn(Flux.just(response).concatWith(Flux.never()));
            var stream = new PerformanceMonitorAdvisor().adviseStream(
                    new ChatClientRequest(new Prompt("测试"), Map.of()), chain);
            StepVerifier.create(stream).expectNext(response).thenCancel().verify();
            assertThat(events.list).filteredOn(event -> event.getFormattedMessage().contains("cancel")).hasSize(1);
            assertThat(events.list).noneMatch(event -> event.getFormattedMessage().contains("不应写入日志的正文"));
        } finally {
            logger.detachAppender(events);
            logger.setLevel(previousLevel);
            events.stop();
        }
    }
}
