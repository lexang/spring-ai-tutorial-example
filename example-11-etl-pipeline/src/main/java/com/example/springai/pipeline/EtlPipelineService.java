package com.example.springai.pipeline;

import com.example.springai.support.EtlOperationException;
import com.example.springai.support.DocumentEnrichmentValidator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 将 Reader、Transformer 和 VectorStore Writer 组合成可测试的 ETL 服务。
 */
@Service
public class EtlPipelineService {

    private static final List<Character> PUNCTUATION_MARKS =
            List.of('.', '?', '!', '\n', '。', '？', '！', '；');

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private final ObjectProvider<ChatModel> chatModelProvider;

    private final Resource textResource;

    private final Resource jsonResource;

    private final Resource markdownResource;

    private final Set<String> loadedDocumentIds = ConcurrentHashMap.newKeySet();

    /** 串行化写入与清理，避免清理快照和写入跟踪之间出现竞态。 */
    private final ReentrantLock vectorStoreMutationLock = new ReentrantLock();

    public EtlPipelineService(
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("classpath:samples/sample.txt") Resource textResource,
            @Value("classpath:samples/sample.json") Resource jsonResource,
            @Value("classpath:samples/sample.md") Resource markdownResource) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.chatModelProvider = chatModelProvider;
        this.textResource = textResource;
        this.jsonResource = jsonResource;
        this.markdownResource = markdownResource;
    }

    /** 只读取和分块，不获取模型或向量库，也不触发写入。 */
    public PipelineRun preview(String requestedFormat) {
        String format = normalizeFormat(requestedFormat);
        List<Document> extracted = extract(format);
        List<Document> chunks = createSplitter().transform(extracted);
        return new PipelineRun(format, extracted.size(), chunks.size(), 0, List.copyOf(chunks));
    }

    public PipelineRun process(String requestedFormat, boolean enableEnrich, int enrichLimit) {
        String format = normalizeFormat(requestedFormat);
        if (enableEnrich) {
            requireRange("enrichLimit", enrichLimit, 1, 20);
        }

        List<Document> extractedDocuments = extract(format);
        TransformResult transformResult = transform(extractedDocuments, enableEnrich, enrichLimit);
        write(transformResult.documents());

        return new PipelineRun(
                format,
                extractedDocuments.size(),
                transformResult.documents().size(),
                transformResult.enrichedDocumentCount(),
                List.copyOf(transformResult.documents())
        );
    }

    public PipelineRun functionalPipeline() {
        TextReader reader = new TextReader(this.textResource);
        TokenTextSplitter splitter = createSplitter();
        List<Document> documents = splitter.split(reader.read());

        // VectorStore 实现了 DocumentWriter，因此可以使用 write/accept 两种等价写法。
        write(documents);
        return new PipelineRun("text", 1, documents.size(), 0, List.copyOf(documents));
    }

    public List<Document> search(String query, int topK) {
        if (query == null || query.isBlank() || query.length() > 500) {
            throw new IllegalArgumentException("query 不能为空，且长度不能超过 500。");
        }
        requireRange("topK", topK, 1, 20);

        return requireVectorStore().similaritySearch(SearchRequest.builder()
                .query(query.trim())
                .topK(topK)
                .build());
    }

    public int clearLoadedDocuments() {
        this.vectorStoreMutationLock.lock();
        try {
            List<String> ids = List.copyOf(this.loadedDocumentIds);
            if (ids.isEmpty()) {
                return 0;
            }
            requireVectorStore().delete(ids);
            this.loadedDocumentIds.removeAll(ids);
            return ids.size();
        }
        finally {
            this.vectorStoreMutationLock.unlock();
        }
    }

    public int loadedDocumentCount() {
        return this.loadedDocumentIds.size();
    }

    public String vectorStoreName() {
        VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
        return vectorStore == null ? "未启用" : vectorStore.getName();
    }

    List<Document> extract(String format) {
        return switch (format) {
            case "text" -> {
                TextReader reader = new TextReader(this.textResource);
                reader.getCustomMetadata().put("format", "text");
                yield reader.read();
            }
            case "json" -> new JsonReader(this.jsonResource, "title", "content").read();
            case "markdown" -> {
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withAdditionalMetadata("format", "markdown")
                        .build();
                yield new MarkdownDocumentReader(this.markdownResource, config).read();
            }
            default -> throw new IllegalArgumentException("不支持的文档格式：" + format);
        };
    }

    private TransformResult transform(List<Document> documents, boolean enableEnrich, int enrichLimit) {
        List<Document> chunks = createSplitter().transform(documents);
        if (!enableEnrich || chunks.isEmpty()) {
            return new TransformResult(chunks, 0);
        }

        int enrichedDocumentCount = Math.min(enrichLimit, chunks.size());
        List<Document> selectedDocuments = new ArrayList<>(chunks.subList(0, enrichedDocumentCount));
        try {
            DocumentEnrichmentValidator.requireKeywords(KeywordMetadataEnricher.builder(requireChatModel())
                    .keywordCount(5)
                    .build()
                    .transform(selectedDocuments));
        }
        catch (EtlOperationException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new EtlOperationException("关键词元数据增强失败，请检查 ChatModel 配置和响应。", exception);
        }
        return new TransformResult(chunks, enrichedDocumentCount);
    }

    private void write(List<Document> documents) {
        this.vectorStoreMutationLock.lock();
        try {
            requireVectorStore().write(documents);
            trackDocuments(documents);
        }
        finally {
            this.vectorStoreMutationLock.unlock();
        }
    }

    private void trackDocuments(List<Document> documents) {
        documents.stream().map(Document::getId).forEach(this.loadedDocumentIds::add);
    }

    private TokenTextSplitter createSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .withPunctuationMarks(PUNCTUATION_MARKS)
                .build();
    }

    private String normalizeFormat(String requestedFormat) {
        if (requestedFormat == null || requestedFormat.isBlank()) {
            throw new IllegalArgumentException("format 不能为空。");
        }
        String format = requestedFormat.trim().toLowerCase(Locale.ROOT);
        if (!List.of("text", "json", "markdown").contains(format)) {
            throw new IllegalArgumentException("format 仅支持 text、json 或 markdown。");
        }
        return format;
    }

    private ChatModel requireChatModel() {
        ChatModel chatModel = this.chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new EtlOperationException(
                    "当前未启用 ChatModel。请设置 SPRING_AI_CHAT_MODEL=anthropic 和 MINIMAX_API_KEY 后重启应用。");
        }
        return chatModel;
    }

    private VectorStore requireVectorStore() {
        VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new EtlOperationException(
                    "当前未启用 EmbeddingModel 和 VectorStore。请设置 SPRING_AI_EMBEDDING_MODEL=transformers，并准备可访问的 ONNX 模型 URI。");
        }
        return vectorStore;
    }

    private void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " 必须在 " + min + " 到 " + max + " 之间。");
        }
    }

    public record PipelineRun(
            String format,
            int extractedDocumentCount,
            int transformedDocumentCount,
            int enrichedDocumentCount,
            List<Document> documents) {
    }

    private record TransformResult(List<Document> documents, int enrichedDocumentCount) {
    }
}
