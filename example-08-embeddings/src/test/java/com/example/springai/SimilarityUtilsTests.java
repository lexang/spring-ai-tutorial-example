package com.example.springai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimilarityUtilsTests {

    @Test
    void shouldCalculateCommonSimilarityMetrics() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};

        assertThat(SimilarityUtils.cosineSimilarity(a, a)).isEqualTo(1.0);
        assertThat(SimilarityUtils.cosineSimilarity(a, b)).isZero();
        assertThat(SimilarityUtils.euclideanDistance(a, b)).isEqualTo(Math.sqrt(2.0));
        assertThat(SimilarityUtils.dotProduct(a, b)).isZero();
    }

    @Test
    void shouldNormalizeVectorWithoutChangingZeroVector() {
        assertThat(SimilarityUtils.normalize(new float[] {3.0f, 4.0f}))
                .containsExactly(0.6f, 0.8f);
        assertThat(SimilarityUtils.normalize(new float[] {0.0f, 0.0f}))
                .containsExactly(0.0f, 0.0f);
    }

    @Test
    void shouldRejectDifferentDimensions() {
        assertThatThrownBy(() -> SimilarityUtils.cosineSimilarity(
                new float[] {1.0f}, new float[] {1.0f, 2.0f}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("向量维度必须相同");
    }

    @Test
    void shouldRejectNullAndNonFiniteVectors() {
        assertThatThrownBy(() -> SimilarityUtils.dotProduct(null, new float[] {1.0f}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("向量 A 不能为空");
        assertThatThrownBy(() -> SimilarityUtils.cosineSimilarity(
                new float[] {Float.NaN}, new float[] {1.0f}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("向量必须只包含有限数值");
        assertThatThrownBy(() -> SimilarityUtils.normalize(new float[] {Float.POSITIVE_INFINITY}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("向量必须只包含有限数值");
    }

    @Test
    void shouldRejectInvalidDistance() {
        assertThatThrownBy(() -> SimilarityUtils.distanceToSimilarity(-1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("距离必须是非负有限数");
    }
}
