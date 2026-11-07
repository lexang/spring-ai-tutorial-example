package com.example.springai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 基于 VectorStore 的最小 RAG 问答示例。 */
@Service
public class QAService {

    private static final Logger log = LoggerFactory.getLogger(QAService.class);

    private final VectorStore vectorStore;

    private final ChatClient chatClient;

    @Autowired
    public QAService(VectorStore vectorStore, ObjectProvider<ChatModel> models, ObjectProvider<ChatClient.Builder> builders) {
        // prototype Builder 的定义可能存在，但没有 ChatModel 时不能实例化它。
        this(vectorStore, models.getIfAvailable() == null ? null : builders.getIfAvailable());
    }

    public QAService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder == null ? null : chatClientBuilder.clone().build();
    }

    /** 基于检索到的文本上下文回答问题。 */
    public QAResponse askQuestion(String question) {
        String validatedQuestion = validateQuestion(question);
        log.info("开始问答检索");

        List<Document> relevantDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(validatedQuestion)
                        .topK(5)
                        .build());

        return answer(validatedQuestion, null, relevantDocuments);
    }

    /** 先使用结构化元数据过滤限定类别，再执行问答。 */
    public QAResponse askQuestionWithCategory(String question, String category) {
        String validatedQuestion = validateQuestion(question);
        String validatedCategory = validateCategory(category);

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(validatedQuestion)
                .topK(5);
        if (validatedCategory != null) {
            requestBuilder.filterExpression(
                    new FilterExpressionBuilder().eq("category", validatedCategory).build());
        }

        List<Document> relevantDocuments = vectorStore.similaritySearch(requestBuilder.build());
        return answer(validatedQuestion, validatedCategory, relevantDocuments);
    }

    private QAResponse answer(String question, String category, List<Document> relevantDocuments) {
        List<Document> textDocuments = relevantDocuments.stream()
                .filter(Document::isText)
                .toList();

        log.info("问答检索完成，结果数={}，文本结果数={}", relevantDocuments.size(), textDocuments.size());
        if (textDocuments.isEmpty()) {
            String scope = category == null ? "知识库" : "“" + category + "”类别";
            return new QAResponse("抱歉，在" + scope + "中没有找到可用于回答的文本信息。", List.of());
        }

        String context = textDocuments.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n"));

        String prompt = """
                你是一个中文知识库助手。请仅依据下面的上下文回答问题。
                如果上下文不足以回答，请明确说明，不要补充上下文之外的事实。

                上下文：
                %s

                问题：%s
                """.formatted(context, question);

        if (chatClient == null) {
            throw new ChatDisabledException();
        }
        String generatedAnswer = chatClient.prompt(prompt).call().content();
        if (!StringUtils.hasText(generatedAnswer)) {
            throw new IllegalStateException("聊天模型未返回文本内容");
        }

        List<DocumentSource> sources = textDocuments.stream()
                .map(document -> new DocumentSource(
                        excerpt(document.getText()),
                        Map.copyOf(document.getMetadata()),
                        document.getScore()))
                .toList();

        log.info("问答生成完成，引用数={}", sources.size());
        return new QAResponse(generatedAnswer, sources);
    }

    private String validateQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (question.length() > 2_000) {
            throw new IllegalArgumentException("问题不能超过 2000 个字符");
        }
        return question.trim();
    }

    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String value = category.trim();
        if (value.length() > 100) {
            throw new IllegalArgumentException("分类不能超过 100 个字符");
        }
        return value;
    }

    private String excerpt(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 100 ? text : text.substring(0, 100) + "…";
    }

    /** 问答响应。 */
    public record QAResponse(String answer, List<DocumentSource> sources) {
    }

    /** 被模型回答引用的检索结果。 */
    public record DocumentSource(String excerpt, Map<String, Object> metadata, Double similarity) {
    }

    public static class ChatDisabledException extends RuntimeException {
        public ChatDisabledException() { super("问答模型未启用；检索接口仍可独立使用"); }
    }
}
