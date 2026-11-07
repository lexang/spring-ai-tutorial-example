package com.example.springai.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.example.springai.reader.RequestDocumentReader;
import com.example.springai.support.KnowledgeOperationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBaseService {

    private static final int MAX_CONTENT_LENGTH = 100_000;

    private static final int MAX_QUESTION_LENGTH = 1_000;

    private static final int MAX_TOP_K = 10;

    private final VectorStore vectorStore;

    private final ChatModel chatModel;

    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withChunkSize(300)
        .withMinChunkSizeChars(100)
        .withMinChunkLengthToEmbed(10)
        .withMaxNumChunks(200)
        .withKeepSeparator(true)
        .withPunctuationMarks(List.of('.', '?', '!', '\n', '。', '？', '！', '；'))
        .build();

    private final Map<String, List<String>> sourceChunkIds = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock storeLock = new ReentrantReadWriteLock();

    public KnowledgeBaseService(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    public IngestResult ingest(IngestRequest request) {
        ValidatedDocument validated = validateDocument(request);
        RequestDocumentReader reader = new RequestDocumentReader(validated.title(), validated.content(),
                validated.source(), validated.category());
        List<Document> extracted = reader.read();
        List<Document> splitDocuments = this.splitter.transform(extracted);
        if (splitDocuments.isEmpty()) {
            throw new IllegalArgumentException("content 拆分后没有可写入的有效文本");
        }

        List<Document> chunks = stableChunks(validated.source(), splitDocuments);
        this.storeLock.writeLock().lock();
        try {
            List<String> previousIds = this.sourceChunkIds.getOrDefault(validated.source(), List.of());
            this.vectorStore.add(chunks);
            List<String> newIds = chunks.stream().map(Document::getId).toList();
            List<String> staleIds = previousIds.stream().filter(id -> !newIds.contains(id)).toList();
            if (!staleIds.isEmpty()) {
                this.vectorStore.delete(staleIds);
            }
            this.sourceChunkIds.put(validated.source(), List.copyOf(newIds));
            return new IngestResult(extracted.size(), chunks.size(), !previousIds.isEmpty(), newIds);
        }
        catch (RuntimeException exception) {
            throw operationFailure("文档向量化或写入失败", exception);
        }
        finally {
            this.storeLock.writeLock().unlock();
        }
    }

    public AnswerResult ask(AskRequest request) {
        ValidatedQuestion validated = validateQuestion(request);
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(this.vectorStore)
            .searchRequest(SearchRequest.builder()
                .topK(validated.topK())
                .similarityThreshold(0.0)
                .build())
            .build();

        this.storeLock.readLock().lock();
        try {
            var prompt = ChatClient.create(this.chatModel)
                .prompt()
                .advisors(advisor)
                .user(validated.question());
            if (validated.category() != null) {
                prompt.advisors(spec -> spec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION,
                        "category == '" + validated.category() + "'"));
            }
            ChatClientResponse response = prompt.call().chatClientResponse();
            return toAnswer(validated.question(), response);
        }
        catch (RuntimeException exception) {
            throw operationFailure("文档检索或回答生成失败", exception);
        }
        finally {
            this.storeLock.readLock().unlock();
        }
    }

    public DeleteResult deleteBySource(String requestedSource) {
        String source = requireText(requestedSource, "source", 200);
        this.storeLock.writeLock().lock();
        try {
            List<String> ids = this.sourceChunkIds.get(source);
            if (ids == null || ids.isEmpty()) {
                return new DeleteResult(source, 0);
            }
            this.vectorStore.delete(ids);
            this.sourceChunkIds.remove(source);
            return new DeleteResult(source, ids.size());
        }
        catch (RuntimeException exception) {
            throw operationFailure("删除知识库文档失败", exception);
        }
        finally {
            this.storeLock.writeLock().unlock();
        }
    }

    public KnowledgeStatus status() {
        int sourceCount = this.sourceChunkIds.size();
        int chunkCount = this.sourceChunkIds.values().stream().mapToInt(List::size).sum();
        return new KnowledgeStatus(sourceCount, chunkCount, this.vectorStore.getName(),
                this.chatModel.getClass().getSimpleName());
    }

    private List<Document> stableChunks(String source, List<Document> splitDocuments) {
        List<Document> chunks = new ArrayList<>(splitDocuments.size());
        for (int index = 0; index < splitDocuments.size(); index++) {
            Document split = splitDocuments.get(index);
            Map<String, Object> metadata = new LinkedHashMap<>(split.getMetadata());
            metadata.put("chunk_index", index);
            metadata.put("total_chunks", splitDocuments.size());
            String id = UUID.nameUUIDFromBytes((source + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
            chunks.add(new Document(id, split.getText(), metadata));
        }
        return List.copyOf(chunks);
    }

    private AnswerResult toAnswer(String question, ChatClientResponse response) {
        if (response == null) {
            throw new KnowledgeOperationException("回答模型没有返回响应");
        }
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null
                || !StringUtils.hasText(chatResponse.getResult().getOutput().getText())) {
            throw new KnowledgeOperationException("回答模型没有返回可用文本");
        }

        Object retrieved = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        List<Document> documents = retrieved instanceof List<?> values
                ? values.stream().filter(Document.class::isInstance).map(Document.class::cast).toList()
                : List.of();
        List<Source> sources = documents.stream().map(this::toSource).toList();
        var usage = chatResponse.getMetadata().getUsage();
        boolean reported = usage != null && !(usage instanceof EmptyUsage);
        return new AnswerResult(question,
            sources.isEmpty() ? "知识库没有足够信息，无法依据文档回答。"
                : chatResponse.getResult().getOutput().getText(),
            sources, sources.isEmpty() ? "NO_CONTEXT" : "ANSWERED",
            new UsageSummary(reported, reported ? usage.getPromptTokens() : null,
                reported ? usage.getCompletionTokens() : null, reported ? usage.getTotalTokens() : null));
    }

    private Source toSource(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new Source(
                document.getId(),
                metadata.getOrDefault("title", "未命名文档").toString(),
                metadata.getOrDefault("source", "unknown").toString(),
                metadata.getOrDefault("category", "default").toString(),
                document.getScore(),
                abbreviate(document.getText(), 240));
    }

    private ValidatedDocument validateDocument(IngestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String title = requireText(request.title(), "title", 120);
        String content = requireText(request.content(), "content", MAX_CONTENT_LENGTH);
        if (content.length() < 20) {
            throw new IllegalArgumentException("content 至少需要 20 个字符");
        }
        String source = requireText(request.source(), "source", 200);
        String category = validateCategory(request.category());
        return new ValidatedDocument(title, content, source, category);
    }

    private ValidatedQuestion validateQuestion(AskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String question = requireText(request.question(), "question", MAX_QUESTION_LENGTH);
        int topK = request.topK() == null ? 4 : request.topK();
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK 必须在 1 到 10 之间");
        }
        String category = request.category() == null || request.category().isBlank()
                ? null
                : validateCategory(request.category());
        return new ValidatedQuestion(question, topK, category);
    }

    private String validateCategory(String value) {
        String category = requireText(value, "category", 40).toLowerCase(Locale.ROOT);
        if (!category.matches("[\\p{L}\\p{N}_-]+")) {
            throw new IllegalArgumentException("category 只能包含文字、数字、下划线或连字符");
        }
        return category;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private KnowledgeOperationException operationFailure(String message, RuntimeException exception) {
        if (exception instanceof KnowledgeOperationException operationException) {
            return operationException;
        }
        return new KnowledgeOperationException(message, exception);
    }

    public record IngestRequest(String title, String content, String source, String category) {
    }

    public record IngestResult(int extractedDocumentCount, int chunkCount,
            boolean replacedExistingSource, List<String> chunkIds) {
    }

    public record AskRequest(String question, Integer topK, String category) {
    }

    public record AnswerResult(String question, String answer, List<Source> sources,
            String status, UsageSummary usage) {
    }

    public record Source(String chunkId, String title, String source, String category, Double score, String excerpt) {
    }

    public record UsageSummary(boolean reported, Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    }

    public record DeleteResult(String source, int deletedChunkCount) {
    }

    public record KnowledgeStatus(int sourceCount, int chunkCount, String vectorStore, String chatModel) {
    }

    private record ValidatedDocument(String title, String content, String source, String category) {
    }

    private record ValidatedQuestion(String question, int topK, String category) {
    }

}
