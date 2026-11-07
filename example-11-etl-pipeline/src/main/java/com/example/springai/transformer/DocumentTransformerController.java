package com.example.springai.transformer;

import com.example.springai.support.DocumentResponses;
import com.example.springai.support.DocumentEnrichmentValidator;
import com.example.springai.support.EtlOperationException;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 演示 TokenTextSplitter、ContentFormatTransformer 和两种模型元数据增强器。
 */
@RestController
@RequestMapping("/api/etl/transformer")
public class DocumentTransformerController {

    private static final Logger log = LoggerFactory.getLogger(DocumentTransformerController.class);

    private static final List<Character> CHINESE_PUNCTUATION =
            List.of('.', '?', '!', '\n', ';', ':', '。', '？', '！', '；');

    private final ObjectProvider<ChatModel> chatModelProvider;

    private final Resource textResource;

    public DocumentTransformerController(
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("classpath:samples/sample.txt") Resource textResource) {
        this.chatModelProvider = chatModelProvider;
        this.textResource = textResource;
    }

    @GetMapping("/split")
    public Map<String, Object> splitDocument(
            @RequestParam(defaultValue = "800") int chunkSize,
            @RequestParam(defaultValue = "350") int minChunkSizeChars,
            @RequestParam(defaultValue = "CL100K_BASE") String encoding,
            @RequestParam(defaultValue = "true") boolean chinesePunctuation) {

        TokenTextSplitter splitter = createSplitter(chunkSize, minChunkSizeChars, encoding, chinesePunctuation);
        List<Document> originalDocuments = readTextDocuments();
        List<Document> chunks = splitter.transform(originalDocuments);

        log.info("TokenTextSplitter 完成：原文档 {} 个，分块 {} 个", originalDocuments.size(), chunks.size());

        Map<String, Object> result = DocumentResponses.result(chunks, 1000);
        result.put("originalDocumentCount", originalDocuments.size());
        result.put("parameters", Map.of(
                "encoding", parseEncoding(encoding).name(),
                "chunkSize", chunkSize,
                "minChunkSizeChars", minChunkSizeChars,
                "chinesePunctuation", chinesePunctuation,
                "minChunkLengthToEmbed", 5,
                "maxNumChunks", 10000
        ));
        result.put("important", "minChunkSizeChars 是标点断句的最小字符阈值，不是块之间的 overlap。");
        return result;
    }

    @GetMapping("/split/compare")
    public Map<String, Object> compareSplitParameters() {
        List<Document> documents = readTextDocuments();
        int[][] configurations = {
                {200, 80},
                {400, 150},
                {800, 350}
        };

        List<Map<String, Object>> comparisons = new ArrayList<>();
        for (int[] configuration : configurations) {
            int chunkSize = configuration[0];
            int minChunkSizeChars = configuration[1];
            List<Document> chunks = createSplitter(chunkSize, minChunkSizeChars, "CL100K_BASE", true)
                    .split(documents);

            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("chunkSize", chunkSize);
            comparison.put("minChunkSizeChars", minChunkSizeChars);
            comparison.put("resultCount", chunks.size());
            comparison.put("averageCharacters", chunks.stream()
                    .map(Document::getText)
                    .mapToInt(text -> text == null ? 0 : text.length())
                    .average()
                    .orElse(0));
            comparisons.add(comparison);
        }

        return Map.of(
                "comparisons", comparisons,
                "note", "本接口比较目标 token 大小与标点断句阈值，不演示 overlap；TokenTextSplitter 没有 overlap 参数。"
        );
    }

    @GetMapping("/format")
    public Map<String, Object> formatDocumentContent() {
        TextReader reader = new TextReader(this.textResource);
        reader.getCustomMetadata().put("category", "ETL 教程");
        List<Document> documents = reader.read();

        DefaultContentFormatter formatter = DefaultContentFormatter.builder()
                .withExcludedInferenceMetadataKeys("charset", "source")
                .withExcludedEmbedMetadataKeys("charset")
                .build();
        new ContentFormatTransformer(formatter).transform(documents);

        List<Map<String, Object>> formattedDocuments = documents.stream().map(document -> Map.<String, Object>of(
                "id", document.getId(),
                "formattedAll", document.getFormattedContent(MetadataMode.ALL),
                "formattedForInference", document.getFormattedContent(MetadataMode.INFERENCE),
                "formattedForEmbedding", document.getFormattedContent(MetadataMode.EMBED),
                "rawTextUnchanged", document.getText()
        )).toList();

        return Map.of(
                "documentCount", documents.size(),
                "documents", formattedDocuments,
                "note", "当两侧都是 DefaultContentFormatter 时，转换器合并元数据排除规则并保留原模板；原始 text 不会改变。"
        );
    }

    @GetMapping("/enrich/keywords")
    public Map<String, Object> enrichWithKeywords(
            @RequestParam(defaultValue = "5") int keywordCount,
            @RequestParam(defaultValue = "3") int documentLimit) {

        requireRange("keywordCount", keywordCount, 1, 20);
        requireRange("documentLimit", documentLimit, 1, 10);

        List<Document> chunks = splitForEnrichment();
        List<Document> selectedDocuments = selectDocuments(chunks, documentLimit);
        KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(requireChatModel())
                .keywordCount(keywordCount)
                .build();
        List<Document> enrichedDocuments = applyKeywordEnricher(enricher, selectedDocuments);

        Map<String, Object> result = DocumentResponses.result(enrichedDocuments, 500);
        result.put("keywordCount", keywordCount);
        result.put("totalSplitDocumentCount", chunks.size());
        result.put("processedDocumentCount", enrichedDocuments.size());
        result.put("metadataKey", "excerpt_keywords");
        result.put("note", "为了控制模型调用成本，本端点只增强 documentLimit 指定的前 N 个块。");
        return result;
    }

    @GetMapping("/enrich/summary")
    public Map<String, Object> enrichWithSummary(
            @RequestParam(defaultValue = "3") int documentLimit) {

        requireRange("documentLimit", documentLimit, 1, 10);
        List<Document> chunks = splitForEnrichment();
        List<Document> selectedDocuments = selectDocuments(chunks, documentLimit);

        SummaryMetadataEnricher enricher = new SummaryMetadataEnricher(
                requireChatModel(),
                List.of(SummaryType.PREVIOUS, SummaryType.CURRENT, SummaryType.NEXT)
        );
        List<Document> enrichedDocuments = applySummaryEnricher(enricher, selectedDocuments);

        Map<String, Object> result = DocumentResponses.result(enrichedDocuments, 500);
        result.put("totalSplitDocumentCount", chunks.size());
        result.put("processedDocumentCount", enrichedDocuments.size());
        result.put("metadataKeys", List.of(
                "section_summary", "prev_section_summary", "next_section_summary"));
        result.put("note", "PREVIOUS/NEXT 是相对于本次送入增强器的有序文档列表计算的。");
        return result;
    }

    @GetMapping("/chain")
    public Map<String, Object> chainTransformers() {
        List<Document> originalDocuments = readTextDocuments();
        List<Document> chunks = createSplitter(200, 80, "CL100K_BASE", true)
                .transform(originalDocuments);
        List<Document> selectedDocuments = selectDocuments(chunks, 2);
        KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(requireChatModel())
                .keywordCount(5)
                .build();
        List<Document> enrichedDocuments = applyKeywordEnricher(enricher, selectedDocuments);

        Map<String, Object> result = DocumentResponses.result(enrichedDocuments, 500);
        result.put("steps", List.of(
                "TextReader.read()",
                "TokenTextSplitter.transform()",
                "KeywordMetadataEnricher.transform()"
        ));
        result.put("statistics", Map.of(
                "read", originalDocuments.size(),
                "split", chunks.size(),
                "enriched", enrichedDocuments.size()
        ));
        return result;
    }

    private List<Document> readTextDocuments() {
        TextReader reader = new TextReader(this.textResource);
        reader.getCustomMetadata().put("sourceType", "classpath-sample");
        return reader.read();
    }

    private List<Document> splitForEnrichment() {
        return createSplitter(120, 40, "CL100K_BASE", true).split(readTextDocuments());
    }

    private List<Document> selectDocuments(List<Document> documents, int limit) {
        return new ArrayList<>(documents.subList(0, Math.min(limit, documents.size())));
    }

    private List<Document> applyKeywordEnricher(KeywordMetadataEnricher enricher, List<Document> documents) {
        try {
            return DocumentEnrichmentValidator.requireKeywords(enricher.transform(documents));
        }
        catch (EtlOperationException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new EtlOperationException("关键词元数据增强失败，请检查 ChatModel 配置和响应。", exception);
        }
    }

    private List<Document> applySummaryEnricher(SummaryMetadataEnricher enricher, List<Document> documents) {
        try {
            return DocumentEnrichmentValidator.requireCurrentSummaries(enricher.transform(documents));
        }
        catch (EtlOperationException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new EtlOperationException("摘要元数据增强失败，请检查 ChatModel 配置和响应。", exception);
        }
    }

    private TokenTextSplitter createSplitter(
            int chunkSize,
            int minChunkSizeChars,
            String encoding,
            boolean chinesePunctuation) {

        requireRange("chunkSize", chunkSize, 1, 8192);
        requireRange("minChunkSizeChars", minChunkSizeChars, 0, 10000);

        TokenTextSplitter.Builder builder = TokenTextSplitter.builder()
                .withEncodingType(parseEncoding(encoding))
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true);

        if (chinesePunctuation) {
            builder.withPunctuationMarks(CHINESE_PUNCTUATION);
        }
        return builder.build();
    }

    private EncodingType parseEncoding(String encoding) {
        try {
            return EncodingType.valueOf(encoding.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "encoding 仅支持 CL100K_BASE、P50K_BASE 或 O200K_BASE。", exception);
        }
    }

    private ChatModel requireChatModel() {
        ChatModel chatModel = this.chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new EtlOperationException(
                    "当前未启用 ChatModel。请设置 SPRING_AI_CHAT_MODEL=anthropic 和 MINIMAX_API_KEY 后重启应用。");
        }
        return chatModel;
    }

    private void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " 必须在 " + min + " 到 " + max + " 之间。");
        }
    }
}
