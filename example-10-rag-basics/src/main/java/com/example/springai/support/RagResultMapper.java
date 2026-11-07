package com.example.springai.support;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;

/** 检索来源只是候选上下文，不把它们包装成经过事实核验的模型引用。 */
public final class RagResultMapper {
    private RagResultMapper() { }

    public static RagAnswer from(ChatClientResponse response, String documentContextKey) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null) {
            throw new RagUpstreamException("模型未返回有效响应");
        }
        Object raw = response.context().get(documentContextKey);
        if (!(raw instanceof List<?> values) || values.stream().anyMatch(value -> !(value instanceof Document))) {
            throw new RagUpstreamException("检索组件未返回有效来源列表");
        }
        List<RetrievedSource> sources = values.stream().map(value -> (Document) value)
                .map(document -> new RetrievedSource(document.getId(), excerpt(document.getText()),
                        Map.copyOf(document.getMetadata()), document.getScore())).toList();
        Usage usage = response.chatResponse().getMetadata().getUsage();
        AnswerUsage answerUsage = usage == null || usage instanceof EmptyUsage
                ? new AnswerUsage(null, null, null)
                : new AnswerUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        if (sources.isEmpty()) {
            // Advisor 已发出模型请求；此处只控制应用返回，不声称跳过了计费调用。
            return new RagAnswer("NO_CONTEXT", "没有检索到可用资料，请调整问题或资料范围", sources, answerUsage);
        }
        String answer = RagResponseValidator.requireContent(response.chatResponse().getResult().getOutput().getText());
        return new RagAnswer("ANSWERED", answer, sources, answerUsage);
    }

    private static String excerpt(String text) {
        if (text == null) { return ""; }
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    public record RagAnswer(String status, String answer, List<RetrievedSource> retrievedSources,
            AnswerUsage answerUsage) { }
    public record RetrievedSource(String id, String excerpt, Map<String, Object> metadata, Double score) { }
    /** 仅回答生成调用的 Usage；不把改写和扩展请求算成已包含。 */
    public record AnswerUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) { }
}
