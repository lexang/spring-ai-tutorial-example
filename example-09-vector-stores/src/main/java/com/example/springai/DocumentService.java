package com.example.springai;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 文档服务：封装 VectorStore 的写入、检索和删除操作。
 *
 * <p>示例只接受适合跨向量数据库移植的简单元数据标量（字符串、数字和布尔值）。</p>
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final int MAX_BATCH_SIZE = 1_000;

    private static final int MAX_CONTENT_LENGTH = 100_000;

    private static final int MAX_METADATA_ENTRIES = 50;

    private static final int MAX_TOP_K = 100;

    private static final int MAX_FILTER_LENGTH = 1_000;

    private final VectorStore vectorStore;

    public DocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 添加单个文档，并返回 Spring AI 生成的文档 ID。 */
    public String addDocument(String content, Map<String, Object> metadata) {
        String validatedContent = validateContent(content);
        Map<String, Object> enrichedMetadata = copyAndValidateMetadata(metadata);
        enrichedMetadata.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString());

        Document document = new Document(validatedContent, enrichedMetadata);
        vectorStore.add(List.of(document));

        log.info("文档已添加，id={}", document.getId());
        return document.getId();
    }

    /** 批量添加文档。VectorStore 的实际批处理上限由 EmbeddingModel 和后端决定。 */
    public void addDocuments(List<DocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("文档列表不能为空");
        }
        if (requests.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次最多添加 " + MAX_BATCH_SIZE + " 个文档");
        }

        List<Document> documents = requests.stream()
                .map(request -> {
                    if (request == null) {
                        throw new IllegalArgumentException("文档请求不能为 null");
                    }
                    String content = validateContent(request.content());
                    Map<String, Object> metadata = copyAndValidateMetadata(request.metadata());
                    metadata.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
                    return new Document(content, metadata);
                })
                .toList();

        vectorStore.add(documents);
        log.info("批量添加完成，数量={}", documents.size());
    }

    /** 使用 SearchRequest 执行相似度搜索。 */
    public List<SearchResult> search(String query, Integer topK) {
        String validatedQuery = validateQuery(query);
        int validatedTopK = validateTopK(topK);

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(validatedQuery)
                        .topK(validatedTopK)
                        .build());

        log.info("相似度搜索完成，topK={}，结果数={}", validatedTopK, documents.size());
        return toSearchResults(documents);
    }

    /** 使用文本过滤表达式执行搜索。 */
    public List<SearchResult> searchWithFilter(String query, String filterExpression,
            Integer topK, Double similarityThreshold) {
        String validatedQuery = validateQuery(query);
        int validatedTopK = validateTopK(topK);
        String validatedFilter = validateFilter(filterExpression, false);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(validatedQuery)
                .topK(validatedTopK);

        if (similarityThreshold != null) {
            if (!Double.isFinite(similarityThreshold)
                    || similarityThreshold < 0.0 || similarityThreshold > 1.0) {
                throw new IllegalArgumentException("相似度阈值必须是 0 到 1 之间的有限数字");
            }
            builder.similarityThreshold(similarityThreshold);
        }

        if (validatedFilter != null) {
            try {
                builder.filterExpression(validatedFilter);
            }
            catch (RuntimeException exception) {
                throw new IllegalArgumentException("过滤表达式无效，请检查字段、运算符和引号", exception);
            }
        }

        return toSearchResults(vectorStore.similaritySearch(builder.build()));
    }

    /** 使用 FilterExpressionBuilder 构造结构化过滤条件，避免拼接用户输入。 */
    public List<SearchResult> searchWithDSL(String query, String category, Integer year) {
        return searchWithDSL(query, category, year, null);
    }

    /** 结构化过滤搜索的可调 topK 版本。 */
    public List<SearchResult> searchWithDSL(String query, String category, Integer year, Integer topK) {
        return searchWithDSL(query, category, year, topK, null);
    }

    /** 博客 POST 搜索主线，分类和年份按结构化 DSL 处理。 */
    public List<SearchResult> searchWithDSL(String query, String category, Integer year, Integer topK, Double threshold) {
        String validatedQuery = validateQuery(query);
        int validatedTopK = validateTopK(topK);
        String validatedCategory = validateOptionalText(category, "分类", 100);
        if (year != null && (year < 1_000 || year > 9_999)) {
            throw new IllegalArgumentException("年份必须是四位数字");
        }

        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = null;
        if (validatedCategory != null) {
            filter = filterBuilder.eq("category", validatedCategory);
        }
        if (year != null) {
            FilterExpressionBuilder.Op yearFilter = filterBuilder.gte("year", year);
            filter = filter == null ? yearFilter : filterBuilder.and(filter, yearFilter);
        }

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(validatedQuery)
                .topK(validatedTopK);
        if (threshold != null) {
            if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException("相似度阈值必须是 0 到 1 之间的有限数字");
            }
            requestBuilder.similarityThreshold(threshold);
        }
        if (filter != null) {
            requestBuilder.filterExpression(filter.build());
        }

        return toSearchResults(vectorStore.similaritySearch(requestBuilder.build()));
    }

    /** 按 ID 删除。后端按照官方契约忽略不存在的 ID；失败时抛出异常。 */
    public void deleteById(String id) {
        String validatedId = validateOptionalText(id, "文档 ID", 200);
        if (validatedId == null) {
            throw new IllegalArgumentException("文档 ID 不能为空");
        }
        vectorStore.delete(List.of(validatedId));
        log.info("文档删除完成，id={}", validatedId);
    }

    /** 按文本过滤表达式删除。表达式会由 Spring AI 解析为 Filter.Expression。 */
    public void deleteByFilter(String filterExpression) {
        String validatedFilter = validateFilter(filterExpression, true);
        Filter.Expression parsedExpression;
        try {
            parsedExpression = SearchRequest.builder()
                    .filterExpression(validatedFilter)
                    .build()
                    .getFilterExpression();
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException("删除过滤表达式无效，请检查字段、运算符和引号", exception);
        }
        if (parsedExpression == null) {
            throw new IllegalArgumentException("删除过滤表达式不能为空");
        }
        vectorStore.delete(parsedExpression);
        log.info("按过滤条件删除完成");
    }

    private String validateContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("文档内容不能超过 " + MAX_CONTENT_LENGTH + " 个字符，请先分块");
        }
        return content;
    }

    private String validateQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("搜索查询不能为空");
        }
        return query;
    }

    private int validateTopK(Integer topK) {
        int value = topK != null ? topK : SearchRequest.DEFAULT_TOP_K;
        if (value < 1 || value > MAX_TOP_K) {
            throw new IllegalArgumentException("topK 必须在 1 到 " + MAX_TOP_K + " 之间");
        }
        return value;
    }

    private String validateFilter(String filterExpression, boolean required) {
        String value = validateOptionalText(filterExpression, "过滤表达式", MAX_FILTER_LENGTH);
        if (required && value == null) {
            throw new IllegalArgumentException("过滤表达式不能为空");
        }
        return value;
    }

    private String validateOptionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private Map<String, Object> copyAndValidateMetadata(Map<String, Object> metadata) {
        Map<String, Object> copy = new HashMap<>();
        if (metadata == null) {
            return copy;
        }
        if (metadata.size() > MAX_METADATA_ENTRIES) {
            throw new IllegalArgumentException("元数据字段不能超过 " + MAX_METADATA_ENTRIES + " 个");
        }
        metadata.forEach((key, value) -> {
            if (!StringUtils.hasText(key)) {
                throw new IllegalArgumentException("元数据键不能为空");
            }
            if (value == null) {
                throw new IllegalArgumentException("元数据值不能为 null: " + key);
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("元数据值必须是简单标量（字符串、数字或布尔值）: " + key);
            }
            if (value instanceof Double number && !Double.isFinite(number)) {
                throw new IllegalArgumentException("元数据数字必须是有限值: " + key);
            }
            if (value instanceof Float number && !Float.isFinite(number)) {
                throw new IllegalArgumentException("元数据数字必须是有限值: " + key);
            }
            copy.put(key.trim(), value);
        });
        return copy;
    }

    private List<SearchResult> toSearchResults(List<Document> documents) {
        return documents.stream()
                .map(document -> new SearchResult(
                        document.getId(),
                        document.getText(),
                        Map.copyOf(document.getMetadata()),
                        document.getScore()))
                .toList();
    }

    /** 文档写入请求。 */
    public record DocumentRequest(String content, Map<String, Object> metadata) {
    }

    /** 搜索结果；score 与官方 Document.score 一样允许为 null。 */
    public record SearchResult(
            String id,
            String content,
            Map<String, Object> metadata,
            Double similarity) {
    }
}
