package com.example.springai;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmbeddingBatchTests {
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final EmbeddingBatchController controller = new EmbeddingBatchController(model);

    @Test void validatesInputsBeforeCallingTheModel() {
        assertThatThrownBy(() -> controller.analyze(new EmbeddingBatchController.BatchRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.analyze(new EmbeddingBatchController.BatchRequest(Arrays.asList("一", null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.analyze(new EmbeddingBatchController.BatchRequest(List.of(" "))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(model);
    }

    @Test void ordersResultsByTheReportedInputIndex() {
        when(model.embedForResponse(anyList())).thenReturn(response(
                new Embedding(new float[] {0, 1}, 1), new Embedding(new float[] {1, 0}, 0)));
        var result = controller.analyze(new EmbeddingBatchController.BatchRequest(List.of("Java", "Docker")));
        assertThat(result.vectors().get(0).vector()).containsExactly(1, 0);
        assertThat(result.dimensions()).isEqualTo(2);
        assertThat(result.cosineSimilarity()).isZero();
        assertThat(result.totalTokens()).isNull();
    }

    @Test void retainsReportedUsageWithoutInventingLocalUsage() {
        when(model.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(
                List.of(new Embedding(new float[] {1, 0}, 0)),
                new EmbeddingResponseMetadata("固定模型", new DefaultUsage(7, 0, 7))));
        var result = controller.analyze(new EmbeddingBatchController.BatchRequest(List.of("Java")));
        assertThat(result.promptTokens()).isEqualTo(7);
        assertThat(result.totalTokens()).isEqualTo(7);
        assertThat(result.cosineSimilarity()).isNull();
    }

    @Test void rejectsWrongResultCount() {
        when(model.embedForResponse(anyList())).thenReturn(response());
        assertFailure(List.of("Java"));
    }

    @Test void rejectsDuplicateIndexes() {
        when(model.embedForResponse(anyList())).thenReturn(response(
                new Embedding(new float[] {1, 0}, 0), new Embedding(new float[] {0, 1}, 0)));
        assertFailure(List.of("Java", "Docker"));
    }

    @Test void rejectsOutOfRangeIndexes() {
        when(model.embedForResponse(anyList())).thenReturn(response(new Embedding(new float[] {1, 0}, 2)));
        assertFailure(List.of("Java"));
    }

    @Test void rejectsInconsistentDimensions() {
        when(model.embedForResponse(anyList())).thenReturn(response(
                new Embedding(new float[] {1, 0}, 0), new Embedding(new float[] {0, 1, 0}, 1)));
        assertFailure(List.of("Java", "Docker"));
    }

    @Test void rejectsNonFiniteElements() {
        when(model.embedForResponse(anyList())).thenReturn(response(new Embedding(new float[] {Float.NaN}, 0)));
        assertFailure(List.of("Java"));
    }

    @Test void rejectsEmptyVector() {
        when(model.embedForResponse(anyList())).thenReturn(response(new Embedding(new float[0], 0)));
        assertFailure(List.of("Java"));
    }

    @Test void floatMultiplicationDoesNotOverflowBeforeDoubleAccumulation() {
        float[] vector = {Float.MAX_VALUE, Float.MAX_VALUE};
        assertThat(SimilarityUtils.cosineSimilarity(vector, vector)).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-10));
        assertThat(SimilarityUtils.normalize(vector)[0]).isCloseTo((float) (1 / Math.sqrt(2)),
                org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test void returns400ForInputAnd502ForModelFailures() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/embedding/analyze").contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[\" \"]}")).andExpect(status().isBadRequest());
        when(model.embedForResponse(anyList())).thenThrow(new IllegalArgumentException("内部文件路径和密钥"));
        String body = mvc.perform(post("/api/embedding/analyze").contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[\"Java\"]}")).andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("内部文件路径和密钥");
    }

    private void assertFailure(List<String> texts) {
        assertThatThrownBy(() -> controller.analyze(new EmbeddingBatchController.BatchRequest(texts)))
                .isInstanceOf(EmbeddingBatchController.EmbeddingFailureException.class);
    }
    private static EmbeddingResponse response(Embedding... items) { return new EmbeddingResponse(List.of(items)); }
}
