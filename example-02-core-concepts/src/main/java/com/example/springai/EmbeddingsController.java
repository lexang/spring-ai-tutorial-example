package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embeddings 示例控制器。
 * 演示文本向量化和应用层余弦相似度计算。
 */
@RestController
@RequestMapping("/embeddings")
public class EmbeddingsController {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingsController.class);

    private final EmbeddingModel embeddingModel;

    public EmbeddingsController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/generate")
    public Map<String, Object> generateEmbedding(@RequestParam String text) {
        requireText(text, "text");
        log.info("正在生成文本 Embedding，文本长度：{}", text.length());

        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        float[] embedding = firstEmbedding(response);

        return Map.of(
                "text", text,
                "embedding", Map.of(
                        "dimensions", embedding.length,
                        "preview", toList(embedding, 10),
                        "note", "只显示前 10 个维度；实际维度由模型和配置决定"
                )
        );
    }

    @GetMapping("/similarity")
    public Map<String, Object> calculateSimilarity(
            @RequestParam String text1,
            @RequestParam String text2) {

        requireText(text1, "text1");
        requireText(text2, "text2");
        log.info("正在计算两段文本的余弦相似度");

        float[] embedding1 = embeddingModel.embed(text1);
        float[] embedding2 = embeddingModel.embed(text2);
        double similarity = cosineSimilarity(embedding1, embedding2);

        return Map.of(
                "text1", text1,
                "text2", text2,
                "metric", "cosine",
                "similarity", similarity,
                "interpretation", interpretSimilarity(similarity),
                "note", "阈值仅为演示启发式，生产系统应使用固定数据集评估"
        );
    }

    @GetMapping("/find-similar")
    public Map<String, Object> findSimilar(@RequestParam String query) {
        requireText(query, "query");
        String[] candidates = {
                "提升 Java 应用速度的方法",
                "Java 内存管理技巧",
                "Python 数据分析入门",
                "如何优化数据库查询",
                "前端性能优化指南",
                "Java 多线程编程"
        };

        float[] queryEmbedding = embeddingModel.embed(query);
        List<Map<String, Object>> results = new ArrayList<>();

        for (String candidate : candidates) {
            float[] candidateEmbedding = embeddingModel.embed(candidate);
            double similarity = cosineSimilarity(queryEmbedding, candidateEmbedding);
            results.add(Map.of(
                    "text", candidate,
                    "similarity", similarity,
                    "interpretation", interpretSimilarity(similarity)
            ));
        }

        results.sort((a, b) -> Double.compare(
                (Double) b.get("similarity"),
                (Double) a.get("similarity")
        ));

        return Map.of(
                "query", query,
                "metric", "cosine",
                "results", results
        );
    }

    /**
     * 计算两个向量的余弦相似度。
     * <p>零向量的方向没有定义，本示例按 0.0 处理，避免返回 NaN。</p>
     */
    static double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null) {
            throw new IllegalArgumentException("向量不能为 null");
        }
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("两个向量的维度不一致");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            if (!Float.isFinite(vec1[i]) || !Float.isFinite(vec2[i])) {
                throw new IllegalArgumentException("向量必须只包含有限数值");
            }
            double value1 = vec1[i];
            double value2 = vec2[i];
            dotProduct += value1 * value2;
            norm1 += value1 * value1;
            norm2 += value2 * value2;
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denominator == 0.0) {
            return 0.0;
        }

        // 浮点误差可能产生略超出 [-1, 1] 的结果，返回前做边界保护。
        return Math.max(-1.0, Math.min(1.0, dotProduct / denominator));
    }

    /**
     * 将 float[] 转换为 List<Double>，用于 JSON 输出。
     */
    private List<Double> toList(float[] array, int limit) {
        List<Double> list = new ArrayList<>();
        int count = Math.min(limit, array.length);
        for (int i = 0; i < count; i++) {
            list.add((double) array[i]);
        }
        return list;
    }

    private float[] firstEmbedding(EmbeddingResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()
                || response.getResults().get(0).getOutput() == null) {
            throw new IllegalStateException("模型未返回有效的 Embedding 向量");
        }
        return response.getResults().get(0).getOutput();
    }

    private void requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " 不能为空");
        }
    }

    private String interpretSimilarity(double similarity) {
        if (similarity > 0.9) {
            return "非常相似";
        }
        if (similarity > 0.8) {
            return "很相似";
        }
        if (similarity > 0.7) {
            return "较相似";
        }
        if (similarity > 0.5) {
            return "有一定相关性";
        }
        return "不太相关";
    }

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "title", "Embeddings 示例 API",
                "endpoints", List.of(
                        "/embeddings/generate - 生成 Embedding",
                        "/embeddings/similarity - 计算余弦相似度",
                        "/embeddings/find-similar - 查找相似文本"
                )
        );
    }
}
