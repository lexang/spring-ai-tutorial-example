package com.example.springai;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSearchServiceTests {

    private DocumentSearchService service;

    @BeforeEach
    void setUp() {
        this.service = new DocumentSearchService(new DeterministicEmbeddingModel());
        this.service.clear();
    }

    @Test
    void shouldAddDocumentsAndReturnRequestedTopK() {
        service.addDocuments(List.of("Java 企业开发", "家庭烹饪指南"));

        List<DocumentSearchService.SearchResult> results = service.search("Java 教程", 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("Java 企业开发");
        assertThat(service.getDocumentCount()).isEqualTo(2);
        assertThat(service.getEmbeddingDimension()).isEqualTo(2);
    }

    @Test
    void shouldRejectInvalidInputsBeforeCallingModel() {
        assertThatThrownBy(() -> service.addDocument(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档内容不能为空");
        assertThatThrownBy(() -> service.addDocuments(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档列表不能为空");
        assertThatThrownBy(() -> service.search("Java", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK 必须大于 0");
        assertThatThrownBy(() -> service.findSimilar(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档内容不能为空");
    }

    private static final class DeterministicEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(this::vectorFor)
                    .map(vector -> new Embedding(vector, 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(String text) {
            return vectorFor(text);
        }

        @Override
        public float[] embed(Document document) {
            return vectorFor(document.getText());
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(this::vectorFor).toList();
        }

        @Override
        public int dimensions() {
            return 2;
        }

        private float[] vectorFor(String text) {
            if (text != null && text.toLowerCase().contains("java")) {
                return new float[] {1.0f, 0.0f};
            }
            return new float[] {0.0f, 1.0f};
        }
    }
}
