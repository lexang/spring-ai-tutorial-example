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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 固定并发、有限队列和整体等待期限的应用层批处理。 */
@Service
public class BatchChatService {

    private final ChatClient client;
    private final Duration timeout;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16));

    public BatchChatService(ChatClient.Builder builder, @Value("${demo.batch.timeout:30s}") Duration timeout) {
        this.client = builder.clone().build();
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("批处理期限必须大于 0");
        this.timeout = timeout;
    }

    public List<String> chat(List<String> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > 16) {
            throw new InvalidDemoRequestException("批处理消息数必须为 1 到 16");
        }
        List<Callable<String>> tasks = messages.stream().map(message -> {
            String text = DemoRequestValidation.requireText(message, "message");
            return (Callable<String>) () -> ChatResponseSupport.requireContent(
                    this.client.prompt(text).call().content());
        }).toList();
        try {
            var futures = this.executor.invokeAll(tasks, this.timeout.toMillis(), TimeUnit.MILLISECONDS);
            var answers = new java.util.ArrayList<String>();
            for (var future : futures) answers.add(future.get());
            return List.copyOf(answers);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("批处理被中断", exception);
        } catch (ExecutionException | CancellationException exception) {
            throw new IllegalStateException("批处理失败或超过等待期限", exception);
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new IllegalStateException("批处理队列已满或服务已关闭", exception);
        }
    }

    @PreDestroy
    public void close() {
        this.executor.shutdownNow();
    }
}
