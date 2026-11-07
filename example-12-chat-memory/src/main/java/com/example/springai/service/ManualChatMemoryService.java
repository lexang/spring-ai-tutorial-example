package com.example.springai.service;

import java.util.Objects;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 直接使用 ChatModel 时的最小手动记忆流程。
 *
 * <p>该类不是 Spring Bean，避免离线模式因缺少 ChatModel 而无法启动；它由确定性
 * 单元测试覆盖，也对应教程中的 ChatModel 代码块。</p>
 */
public final class ManualChatMemoryService {

    private final ChatModel chatModel;

    private final ChatMemory chatMemory;

    public ManualChatMemoryService(ChatModel chatModel, ChatMemory chatMemory) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel 不能为空");
        this.chatMemory = Objects.requireNonNull(chatMemory, "chatMemory 不能为空");
    }

    public String chat(String conversationId, String userText) {
        UserMessage userMessage = UserMessage.builder().text(userText).build();
        this.chatMemory.add(conversationId, userMessage);

        ChatResponse response = this.chatModel.call(
                new Prompt(this.chatMemory.get(conversationId)));
        var assistantMessage = Objects.requireNonNull(
                response.getResult(), "模型没有返回 Generation").getOutput();
        this.chatMemory.add(conversationId, assistantMessage);
        return Objects.requireNonNullElse(assistantMessage.getText(), "");
    }
}
