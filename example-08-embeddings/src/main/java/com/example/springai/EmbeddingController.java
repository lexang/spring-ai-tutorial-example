package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embeddings API 控制器
 *
 * 提供文本嵌入和语义搜索的 REST API 接口:
 * - /api/embed - 文本向量化
 * - /api/similarity - 相似度计算
 * - /api/search - 语义搜索
 * - /api/documents - 文档管理
 *
 * @author Spring AI Tutorials
 */
@RestController
@RequestMapping("/api")
public class EmbeddingController {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingController.class);

    private final EmbeddingModel embeddingModel;
    private final DocumentSearchService documentSearchService;

    public EmbeddingController(EmbeddingModel embeddingModel, DocumentSearchService documentSearchService) {
        this.embeddingModel = embeddingModel;
        this.documentSearchService = documentSearchService;
    }

    /**
     * 1. 文本嵌入 - 将文本转换为向量
     *
     * GET /api/embed?text=人工智能
     */
    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam String text) {
        requireText(text, "文本");
        log.info("收到文本嵌入请求");

        // 生成嵌入
        float[] embedding = embeddingModel.embed(text);

        Map<String, Object> response = new HashMap<>();
        response.put("text", text);
        response.put("dimension", embedding.length);
        response.put("embedding", embedding);

        log.info("嵌入生成完成,维度: {}", embedding.length);
        return response;
    }

    /**
     * 2. 批量文本嵌入
     *
     * POST /api/embed/batch
     * Body: ["文本1", "文本2", "文本3"]
     */
    @PostMapping("/embed/batch")
    public Map<String, Object> embedBatch(@RequestBody List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        texts.forEach(text -> requireText(text, "文本"));
        log.info("批量嵌入请求: {} 个文本", texts.size());

        long startTime = System.currentTimeMillis();

        // 批量生成嵌入
        List<float[]> embeddings = embeddingModel.embed(texts);
        if (embeddings == null || embeddings.isEmpty() || embeddings.size() != texts.size()) {
            throw new IllegalStateException("嵌入模型返回的向量数量与文本数量不一致");
        }

        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> response = new HashMap<>();
        response.put("count", texts.size());
        response.put("dimension", embeddings.get(0).length);
        response.put("embeddings", embeddings);
        response.put("duration_ms", duration);

        log.info("批量嵌入完成,耗时: {} ms", duration);
        return response;
    }

    /**
     * 3. 计算相似度
     *
     * GET /api/similarity?text1=猫是宠物&text2=狗是宠物
     */
    @GetMapping("/similarity")
    public Map<String, Object> similarity(
            @RequestParam String text1,
            @RequestParam String text2) {
        requireText(text1, "text1");
        requireText(text2, "text2");

        log.info("收到两段文本的相似度计算请求");

        // 生成嵌入
        float[] embedding1 = embeddingModel.embed(text1);
        float[] embedding2 = embeddingModel.embed(text2);

        // 计算各种相似度指标
        double cosineSim = SimilarityUtils.cosineSimilarity(embedding1, embedding2);
        double euclideanDist = SimilarityUtils.euclideanDistance(embedding1, embedding2);
        double dotProduct = SimilarityUtils.dotProduct(embedding1, embedding2);

        Map<String, Object> response = new HashMap<>();
        response.put("text1", text1);
        response.put("text2", text2);
        response.put("cosine_similarity", cosineSim);
        response.put("euclidean_distance", euclideanDist);
        response.put("dot_product", dotProduct);

        log.info("余弦相似度: {}, 欧几里得距离: {}", cosineSim, euclideanDist);
        return response;
    }

    /**
     * 4. 语义搜索
     *
     * GET /api/search?query=Java编程&topK=5
     */
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        requireText(query, "查询文本");
        requireTopK(topK);

        log.info("语义搜索，topK={}", topK);

        List<DocumentSearchService.SearchResult> results = documentSearchService.search(query, topK);

        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("topK", topK);
        response.put("results", results);
        response.put("total_documents", documentSearchService.getDocumentCount());

        log.info("找到 {} 个相关文档", results.size());
        return response;
    }

    /**
     * 5. 添加文档
     *
     * POST /api/documents
     * Body: {"content": "文档内容"}
     */
    @PostMapping("/documents")
    public Map<String, Object> addDocument(@RequestBody Map<String, String> request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String content = request.get("content");
        requireText(content, "文档内容");

        log.info("收到文档添加请求");

        documentSearchService.addDocument(content);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "文档添加成功");
        response.put("total_documents", documentSearchService.getDocumentCount());

        log.info("文档已添加,当前总数: {}", documentSearchService.getDocumentCount());
        return response;
    }

    /**
     * 6. 批量添加文档
     *
     * POST /api/documents/batch
     * Body: ["文档1", "文档2", "文档3"]
     */
    @PostMapping("/documents/batch")
    public Map<String, Object> addDocuments(@RequestBody List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("文档列表不能为空");
        }
        log.info("批量添加文档: {} 个", contents.size());

        documentSearchService.addDocuments(contents);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "批量添加成功");
        response.put("added_count", contents.size());
        response.put("total_documents", documentSearchService.getDocumentCount());

        log.info("批量添加完成");
        return response;
    }

    /**
     * 7. 查找相似文档
     *
     * POST /api/documents/similar
     * Body: {"content": "文档内容", "topK": 3}
     */
    @PostMapping("/documents/similar")
    public Map<String, Object> findSimilar(@RequestBody Map<String, Object> request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Object contentValue = request.get("content");
        if (!(contentValue instanceof String content)) {
            throw new IllegalArgumentException("文档内容必须是字符串");
        }
        requireText(content, "文档内容");
        int topK = parseTopK(request.get("topK"));

        log.info("查找相似文档, topK: {}", topK);

        List<DocumentSearchService.SearchResult> results = documentSearchService.findSimilar(content, topK);

        Map<String, Object> response = new HashMap<>();
        response.put("similar_documents", results);
        response.put("count", results.size());

        log.info("找到 {} 个相似文档", results.size());
        return response;
    }

    /**
     * 8. 获取文档统计
     *
     * GET /api/documents/stats
     */
    @GetMapping("/documents/stats")
    public Map<String, Object> getStats() {
        log.info("获取文档统计信息");

        // 优先读取当前索引的真实向量长度，避免每次统计请求都触发模型调用。
        int dimension = documentSearchService.getEmbeddingDimension();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_documents", documentSearchService.getDocumentCount());
        stats.put("embedding_dimension", dimension);
        stats.put("model_info", Map.of(
                "provider", "Transformers (ONNX)",
                "model", "已配置的 Sentence Transformer"
        ));

        return stats;
    }

    /**
     * 9. 清空文档库
     *
     * DELETE /api/documents
     */
    @DeleteMapping("/documents")
    public Map<String, Object> clearDocuments() {
        log.info("清空文档库");

        documentSearchService.clear();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "文档库已清空");
        response.put("total_documents", documentSearchService.getDocumentCount());

        log.info("文档库已清空");
        return response;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }

    private static void requireTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须大于 0");
        }
    }

    private static int parseTopK(Object value) {
        if (value == null) {
            return 5;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("topK 必须是整数");
        }
        double numericValue = number.doubleValue();
        int topK = number.intValue();
        if (!Double.isFinite(numericValue) || numericValue != topK) {
            throw new IllegalArgumentException("topK 必须是整数");
        }
        requireTopK(topK);
        return topK;
    }

    private static String preview(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
