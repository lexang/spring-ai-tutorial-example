package com.example.springai.model;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 不访问网络的三维嵌入模型，只用于演示 SimpleVectorStore 的观察生命周期。
 */
public final class DeterministicEmbeddingModel extends AbstractEmbeddingModel {

    private static final int DIMENSIONS = 3;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = IntStream.range(0, instructions.size())
            .mapToObj(index -> new Embedding(vector(instructions.get(index)), index))
            .toList();
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vector(String text) {
        int hash = text == null ? 0 : text.hashCode();
        return new float[] {
            1.0f + Math.abs(hash % 97) / 100.0f,
            1.0f + Math.abs((hash / 97) % 89) / 100.0f,
            1.0f + Math.abs((hash / 8633) % 83) / 100.0f
        };
    }

}
