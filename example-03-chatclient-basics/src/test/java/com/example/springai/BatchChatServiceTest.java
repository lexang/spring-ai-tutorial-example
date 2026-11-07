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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchChatServiceTest {

    @Test
    void preservesInputOrderAcrossParallelModelCalls() {
        ChatModel model = prompt -> new ChatResponse(List.of(
                new Generation(new AssistantMessage(prompt.getUserMessage().getText()))));
        BatchChatService service = new BatchChatService(ChatClient.builder(model), Duration.ofSeconds(5));
        try {
            assertThat(service.chat(List.of("一", "二", "三"))).containsExactly("一", "二", "三");
        } finally {
            service.close();
        }
    }

    @Test
    void rejectsAnOversizedBatchBeforeCallingTheModel() {
        ChatModel model = prompt -> { throw new AssertionError("不应调用模型"); };
        BatchChatService service = new BatchChatService(ChatClient.builder(model), Duration.ofSeconds(5));
        try {
            assertThatThrownBy(() -> service.chat(java.util.Collections.nCopies(17, "消息")))
                    .isInstanceOf(InvalidDemoRequestException.class);
        } finally {
            service.close();
        }
    }
}
