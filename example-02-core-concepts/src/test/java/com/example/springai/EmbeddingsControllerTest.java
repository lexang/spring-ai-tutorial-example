package com.example.springai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingsControllerTest {

    @Test
    void identicalVectorsHaveSimilarityOne() {
        assertThat(EmbeddingsController.cosineSimilarity(
                new float[] { 1.0f, 2.0f }, new float[] { 1.0f, 2.0f }))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1.0e-12));
    }

    @Test
    void orthogonalVectorsHaveSimilarityZero() {
        assertThat(EmbeddingsController.cosineSimilarity(
                new float[] { 1.0f, 0.0f }, new float[] { 0.0f, 1.0f }))
                .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1.0e-12));
    }

    @Test
    void zeroVectorUsesDocumentedZeroPolicy() {
        assertThat(EmbeddingsController.cosineSimilarity(
                new float[] { 0.0f, 0.0f }, new float[] { 1.0f, 2.0f }))
                .isEqualTo(0.0);
    }

    @Test
    void differentDimensionsAreRejected() {
        assertThatThrownBy(() -> EmbeddingsController.cosineSimilarity(
                new float[] { 1.0f }, new float[] { 1.0f, 2.0f }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("两个向量的维度不一致");
    }
}
