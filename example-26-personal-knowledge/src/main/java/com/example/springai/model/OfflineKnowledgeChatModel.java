package com.example.springai.model;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

/** 从 QuestionAnswerAdvisor 注入的上下文生成确定性离线答案。 */
public final class OfflineKnowledgeChatModel implements ChatModel {

    private static final String CONTEXT_SEPARATOR = "---------------------";

    @Override
    public ChatResponse call(Prompt prompt) {
        String context = extractContext(prompt.getUserMessage().getText());
        String answer = StringUtils.hasText(context)
                ? "根据个人笔记：" + abbreviate(context.replaceAll("\\s+", " "), 500)
                : "个人知识库中没有足够信息回答这个问题。";
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    private String extractContext(String userText) {
        if (!StringUtils.hasText(userText)) {
            return "";
        }
        int descriptionMarker = userText.indexOf(CONTEXT_SEPARATOR);
        int startMarker = descriptionMarker < 0 ? -1
                : userText.indexOf(CONTEXT_SEPARATOR, descriptionMarker + CONTEXT_SEPARATOR.length());
        if (startMarker < 0) {
            return "";
        }
        int contentStart = startMarker + CONTEXT_SEPARATOR.length();
        int endMarker = userText.indexOf(CONTEXT_SEPARATOR, contentStart);
        return endMarker < 0 ? "" : userText.substring(contentStart, endMarker).trim();
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

}
