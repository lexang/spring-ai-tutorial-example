package com.example.springai.model;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

/** 从 Advisor 注入的上下文生成确定性答案，便于无凭据测试完整 RAG 链路。 */
public final class OfflineKnowledgeChatModel implements ChatModel {

    private static final String CONTEXT_SEPARATOR = "---------------------";

    @Override
    public ChatResponse call(Prompt prompt) {
        String userText = prompt.getUserMessage().getText();
        String context = extractContext(userText);
        String answer = StringUtils.hasText(context)
                ? "根据知识库：" + abbreviate(context.replaceAll("\\s+", " "), 500)
                : "知识库中没有足够信息回答这个问题。";
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    private String extractContext(String userText) {
        if (!StringUtils.hasText(userText)) {
            return "";
        }
        int descriptionMarker = userText.indexOf(CONTEXT_SEPARATOR);
        if (descriptionMarker < 0) {
            return "";
        }
        int startMarker = userText.indexOf(CONTEXT_SEPARATOR,
                descriptionMarker + CONTEXT_SEPARATOR.length());
        if (startMarker < 0) {
            return "";
        }
        int contentStart = startMarker + CONTEXT_SEPARATOR.length();
        int endMarker = userText.indexOf(CONTEXT_SEPARATOR, contentStart);
        if (endMarker < 0) {
            return "";
        }
        return userText.substring(contentStart, endMarker).trim();
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

}
