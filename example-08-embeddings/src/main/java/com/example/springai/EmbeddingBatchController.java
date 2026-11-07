package com.example.springai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向博客主线的批量向量检查接口；不记录输入正文。 */
@RestController
@RequestMapping("/api/embedding")
public class EmbeddingBatchController {
    private final EmbeddingModel model;

    public EmbeddingBatchController(EmbeddingModel model) { this.model = model; }

    @PostMapping("/analyze")
    public BatchResult analyze(@RequestBody BatchRequest request) {
        if (request == null || request.texts() == null || request.texts().isEmpty() || request.texts().size() > 50) {
            throw new IllegalArgumentException("文本列表应包含 1 到 50 条文本");
        }
        List<String> texts = List.copyOf(validateTexts(request.texts()));
        EmbeddingResponse response;
        try {
            response = model.embedForResponse(texts);
            if (response == null || response.getResults().size() != texts.size()) {
                throw new IllegalStateException("模型返回的向量数量与输入数量不一致");
            }
            // 每条文本的结果都要验证，不能只查看第一个向量的长度。
            float[][] ordered = new float[texts.size()][];
            int dimensions = -1;
            for (var item : response.getResults()) {
                Integer index = item.getIndex();
                float[] vector = item.getOutput();
                if (index == null || index < 0 || index >= ordered.length || ordered[index] != null) {
                    throw new IllegalStateException("模型返回的输入索引无效或重复");
                }
                if (vector == null || vector.length == 0 || (dimensions >= 0 && vector.length != dimensions)) {
                    throw new IllegalStateException("模型返回了空向量或不一致的维度");
                }
                for (float value : vector) {
                    if (!Float.isFinite(value)) { throw new IllegalStateException("模型返回了非有限向量元素"); }
                }
                dimensions = vector.length;
                ordered[index] = vector.clone();
            }
            List<VectorItem> vectors = new ArrayList<>();
            for (int i = 0; i < ordered.length; i++) { vectors.add(new VectorItem(i, ordered[i])); }
            Double cosine = ordered.length == 2 ? SimilarityUtils.cosineSimilarity(ordered[0], ordered[1]) : null;
            Usage usage = response.getMetadata().getUsage();
            boolean reported = usage != null && !(usage instanceof EmptyUsage);
            return new BatchResult(dimensions, vectors, cosine, reported ? usage.getPromptTokens() : null,
                    reported ? usage.getTotalTokens() : null);
        }
        catch (RuntimeException exception) {
            throw new EmbeddingFailureException(exception);
        }
    }

    private static List<String> validateTexts(List<String> texts) {
        for (String text : texts) {
            if (text == null || text.isBlank() || text.length() > 2000) {
                throw new IllegalArgumentException("每条文本不能为空，且不能超过 2000 个字符");
            }
        }
        return texts;
    }

    public record BatchRequest(List<String> texts) { }
    public record VectorItem(int inputIndex, float[] vector) { }
    public record BatchResult(int dimensions, List<VectorItem> vectors, Double cosineSimilarity,
            Integer promptTokens, Integer totalTokens) { }
    public static class EmbeddingFailureException extends RuntimeException {
        public EmbeddingFailureException(Throwable cause) { super("嵌入模型执行失败", cause); }
    }
}
