package com.example.springai.model;

import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 离线确定性评估模型替身。
 *
 * <p>真实的 RelevancyEvaluator 会把评估提示词交给一个 ChatModel。
 * 本替身用文本包含关系返回 YES/NO，便于在没有 API Key 时验证
 * 评估器的模板、请求和结果解析。它不是语义评估器，不能替代真实模型。</p>
 */
public final class DeterministicJudgeChatModel implements ChatModel {

    private final UnaryOperator<String> responder;

    public DeterministicJudgeChatModel() {
        this(DeterministicJudgeChatModel::judgeByTextContainment);
    }

    public DeterministicJudgeChatModel(UnaryOperator<String> responder) {
        this.responder = responder;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = prompt.getUserMessage().getText();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(this.responder.apply(text)))));
    }

    @Override
    public ChatOptions getOptions() {
        return ChatOptions.builder().model("offline-judge").temperature(0.0).build();
    }

    private static String judgeByTextContainment(String prompt) {
        if (prompt.contains("Query:") && prompt.contains("Response:") && prompt.contains("Context:")) {
            String response = section(prompt, "Response:", "Context:");
            String context = section(prompt, "Context:", "Answer:");
            return containsNormalized(context, response) ? "YES" : "NO";
        }
        return "NO";
    }

    private static String section(String prompt, String start, String end) {
        int startIndex = prompt.indexOf(start) + start.length();
        int endIndex = prompt.indexOf(end, startIndex);
        return (endIndex >= 0) ? prompt.substring(startIndex, endIndex) : prompt.substring(startIndex);
    }

    private static boolean containsNormalized(String container, String candidate) {
        String normalizedContainer = normalize(container);
        String normalizedCandidate = normalize(candidate);
        return !normalizedCandidate.isEmpty() && normalizedContainer.contains(normalizedCandidate);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT)
            .replaceAll("[\\p{Punct}\\p{IsPunctuation}]", "");
    }

}
