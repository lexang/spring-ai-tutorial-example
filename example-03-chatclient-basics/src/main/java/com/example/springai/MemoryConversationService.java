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

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

/** 使用官方 Memory Advisor；为了教学清晰，同步串行化调用与清理。 */
@Service
public class MemoryConversationService {

    private final ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
    private final ChatClient chatClient;

    public MemoryConversationService(ChatClient.Builder builder) {
        this.chatClient = builder.clone()
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(this.memory).build()).build();
    }

    public synchronized String chat(String conversationId, String message) {
        String id = DemoRequestValidation.requireIdentifier(conversationId, "conversationId");
        String text = DemoRequestValidation.requireText(message, "message");
        return ChatResponseSupport.requireContent(this.chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
                .user(text).call().content());
    }

    public synchronized void clear(String conversationId) {
        this.memory.clear(DemoRequestValidation.requireIdentifier(conversationId, "conversationId"));
    }
}
