package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 文档语义搜索服务
 *
 * 基于向量嵌入实现语义搜索功能:
 * - 添加文档到索引
 * - 根据查询进行语义搜索
 * - 返回最相关的文档
 *
 * @author Spring AI Tutorials
 */
@Service
public class DocumentSearchService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSearchService.class);

    private final EmbeddingModel embeddingModel;

    /**
     * 文档存储列表(带向量嵌入)
     * 在实际应用中,应该使用向量数据库(如 PGVector、Chroma 等)
     */
    private final List<DocumentWithEmbedding> documents = new CopyOnWriteArrayList<>();

    public DocumentSearchService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        // 初始化一些示例文档
        initializeSampleDocuments();
    }

    /**
     * 初始化示例文档
     */
    private void initializeSampleDocuments() {
        log.info("初始化示例文档库...");

        List<String> sampleDocs = List.of(
                "Spring AI 是一个用于构建 AI 应用的 Java 框架,提供了统一的 API 来访问各种 AI 模型。",
                "Java 是一种面向对象的编程语言,广泛应用于企业级应用开发。",
                "向量数据库专门用于存储和检索向量嵌入,支持高效的相似度搜索。",
                "机器学习是人工智能的一个分支,使计算机能够从数据中学习而无需显式编程。",
                "Spring Boot 简化了 Spring 应用的开发,提供了自动配置和约定优于配置的理念。",
                "自然语言处理(NLP)是人工智能的一个领域,专注于计算机与人类语言之间的交互。",
                "Docker 是一个容器化平台,用于开发、部署和运行应用程序。",
                "REST API 是一种基于 HTTP 的 Web 服务架构风格,广泛用于微服务架构。",
                "深度学习使用神经网络来学习数据的表示,在图像识别和语音识别方面取得了突破。",
                "Kubernetes 是一个开源的容器编排平台,用于自动化部署、扩展和管理容器化应用。"
        );

        addDocuments(sampleDocs);

        log.info("已加载 {} 个示例文档", documents.size());
    }

    /**
     * 添加文档到索引
     *
     * @param content 文档内容
     */
    public void addDocument(String content) {
        validateText(content, "文档内容");

        float[] embedding = this.embeddingModel.embed(content);
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("嵌入模型返回了空向量");
        }
        validateEmbeddingDimension(embedding);
        documents.add(new DocumentWithEmbedding(content, embedding));

        log.debug("文档已添加: {},当前文档总数: {}", preview(content, 50), documents.size());
    }

    /**
     * 批量添加文档
     *
     * @param contents 文档内容列表
     */
    public void addDocuments(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("文档列表不能为空");
        }
        contents.forEach(content -> validateText(content, "文档内容"));
        log.info("批量添加 {} 个文档...", contents.size());

        // 批量生成嵌入(更高效)
        List<float[]> embeddings = embeddingModel.embed(contents);
        if (embeddings == null || embeddings.size() != contents.size()) {
            throw new IllegalStateException("嵌入模型返回的向量数量与文档数量不一致");
        }

        List<DocumentWithEmbedding> newDocuments = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            float[] embedding = embeddings.get(i);
            if (embedding == null || embedding.length == 0) {
                throw new IllegalStateException("嵌入模型返回了空向量");
            }
            validateEmbeddingDimension(embedding, newDocuments);
            newDocuments.add(new DocumentWithEmbedding(contents.get(i), embedding));
        }
        documents.addAll(newDocuments);

        log.info("批量添加完成,当前文档总数: {}", documents.size());
    }

    /**
     * 语义搜索
     *
     * @param query 查询文本
     * @param topK  返回最相关的 K 个文档
     * @return 搜索结果列表(按相似度降序)
     */
    public List<SearchResult> search(String query, int topK) {
        validateText(query, "查询文本");
        validateTopK(topK);
        log.info("搜索查询: '{}', topK: {}", query, topK);

        if (documents.isEmpty()) {
            log.warn("文档库为空,无法搜索");
            return List.of();
        }

        // 生成查询的向量嵌入
        float[] queryEmbedding = embeddingModel.embed(query);

        // 计算与所有文档的相似度
        List<SearchResult> results = new ArrayList<>();
        for (DocumentWithEmbedding doc : documents) {
            double similarity = SimilarityUtils.cosineSimilarity(queryEmbedding, doc.embedding());
            results.add(new SearchResult(doc.content(), similarity));
        }

        // 按相似度降序排序,返回 topK 个结果
        List<SearchResult> topResults = results.stream()
                .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                .limit(topK)
                .collect(Collectors.toList());

        log.info("搜索完成,返回 {} 个结果", topResults.size());
        topResults.forEach(result ->
                log.debug("  - [相似度: {}] {}", result.similarity(),
                        preview(result.content(), 60))
        );

        return topResults;
    }

    /**
     * 查找相似文档
     *
     * @param content 文档内容
     * @param topK    返回最相似的 K 个文档
     * @return 相似文档列表
     */
    public List<SearchResult> findSimilar(String content, int topK) {
        validateText(content, "文档内容");
        validateTopK(topK);
        log.info("查找相似文档, topK: {}", topK);

        if (documents.isEmpty()) {
            return List.of();
        }

        float[] contentEmbedding = embeddingModel.embed(content);

        List<SearchResult> results = new ArrayList<>();
        for (DocumentWithEmbedding doc : documents) {
            // 排除自身
            if (doc.content().equals(content)) {
                continue;
            }

            double similarity = SimilarityUtils.cosineSimilarity(contentEmbedding, doc.embedding());
            results.add(new SearchResult(doc.content(), similarity));
        }

        return results.stream()
                .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 获取文档总数
     */
    public int getDocumentCount() {
        return documents.size();
    }

    /**
     * 返回当前索引的向量维度；索引为空时回退到模型的 dimensions()。
     * dimensions() 的默认实现可能触发一次实际嵌入调用。
     */
    public int getEmbeddingDimension() {
        if (!documents.isEmpty()) {
            return documents.get(0).embedding().length;
        }
        return embeddingModel.dimensions();
    }

    /**
     * 清空文档库
     */
    public void clear() {
        documents.clear();
        log.info("文档库已清空");
    }

    /**
     * 文档与嵌入记录
     */
    record DocumentWithEmbedding(String content, float[] embedding) {
    }

    /**
     * 搜索结果记录
     */
    public record SearchResult(String content, double similarity) {
    }

    private static void validateText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }

    private static void validateTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须大于 0");
        }
    }

    private static String preview(String text, int maxLength) {
        if (text == null) {
            return "<空>";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private void validateEmbeddingDimension(float[] embedding) {
        validateEmbeddingDimension(embedding, List.of());
    }

    private void validateEmbeddingDimension(float[] embedding, List<DocumentWithEmbedding> pendingDocuments) {
        int expectedDimension = -1;
        if (!documents.isEmpty()) {
            expectedDimension = documents.get(0).embedding().length;
        }
        if (!pendingDocuments.isEmpty()) {
            int pendingDimension = pendingDocuments.get(0).embedding().length;
            if (expectedDimension >= 0 && pendingDimension != expectedDimension) {
                throw new IllegalArgumentException("嵌入向量维度必须一致；请清空索引后再切换模型或维度");
            }
            expectedDimension = pendingDimension;
        }
        if (expectedDimension >= 0 && embedding.length != expectedDimension) {
            throw new IllegalArgumentException("嵌入向量维度必须一致；请清空索引后再切换模型或维度");
        }
    }
}
