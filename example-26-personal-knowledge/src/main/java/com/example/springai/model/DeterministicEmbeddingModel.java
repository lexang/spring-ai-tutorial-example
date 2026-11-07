package com.example.springai.model;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** 仅用于离线教学的 Unicode n-gram 哈希向量，不代表真实语义模型质量。 */
public final class DeterministicEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 128;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = IntStream.range(0, request.getInstructions().size())
            .mapToObj(index -> new Embedding(vector(request.getInstructions().get(index)), index))
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

    private float[] vector(String value) {
        String text = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "");
        float[] vector = new float[DIMENSIONS];
        int[] codePoints = text.codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            addFeature(vector, Integer.hashCode(codePoints[index]));
            if (index + 1 < codePoints.length) {
                addFeature(vector, 31 * codePoints[index] + codePoints[index + 1]);
            }
        }
        normalize(vector);
        return vector;
    }

    private void addFeature(float[] vector, int hash) {
        vector[Math.floorMod(hash, vector.length)] += 1.0f;
    }

    private void normalize(float[] vector) {
        double magnitude = 0.0;
        for (float value : vector) {
            magnitude += value * value;
        }
        if (magnitude == 0.0) {
            vector[0] = 1.0f;
            return;
        }
        double divisor = Math.sqrt(magnitude);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) (vector[index] / divisor);
        }
    }

}
