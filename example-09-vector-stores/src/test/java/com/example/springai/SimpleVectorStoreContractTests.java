package com.example.springai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleVectorStoreContractTests {

    private VectorStore vectorStore;

    private Document springDocument;

    private Document vectorDocument;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() {
        this.vectorStore = SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build();
        this.springDocument = new Document("spring framework", Map.of("category", "java", "year", 2026));
        this.vectorDocument = new Document("vector database", Map.of("category", "database", "year", 2025));
        this.vectorStore.add(List.of(this.springDocument, this.vectorDocument));
    }

    @Test
    void shouldReturnSimilarityScore() {
        List<Document> results = this.vectorStore.similaritySearch(
                SearchRequest.builder().query("spring").topK(2).build());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getText()).isEqualTo("spring framework");
        assertThat(results.get(0).getScore()).isEqualTo(1.0);
    }

    @Test
    void shouldFilterByMetadataUsingDsl() {
        var filter = new FilterExpressionBuilder().eq("category", "database").build();

        List<Document> results = this.vectorStore.similaritySearch(
                SearchRequest.builder().query("spring").filterExpression(filter).build());

        assertThat(results).extracting(Document::getText).containsExactly("vector database");
    }

    @Test
    void shouldDeleteByFilterExpression() {
        this.vectorStore.delete("category == 'java'");

        List<Document> results = this.vectorStore.similaritySearch("spring");
        assertThat(results).extracting(Document::getText).containsExactly("vector database");
    }

    @Test
    void shouldRespectTopKAndSimilarityThreshold() {
        List<Document> results = this.vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("spring")
                        .topK(1)
                        .similarityThreshold(0.5)
                        .build());

        assertThat(results).extracting(Document::getText).containsExactly("spring framework");
    }

    @Test
    void shouldAllowZeroTopKAsDefinedBySearchRequestContract() {
        List<Document> results = this.vectorStore.similaritySearch(
                SearchRequest.builder().query("spring").topK(0).build());

        assertThat(results).isEmpty();
    }

    @Test
    void shouldDeleteByIdAndIgnoreUnknownId() {
        this.vectorStore.delete(List.of(this.springDocument.getId(), "missing-id"));

        List<Document> results = this.vectorStore.similaritySearch("spring");
        assertThat(results).extracting(Document::getText).containsExactly("vector database");
    }

    @Test
    void shouldSaveAndLoadJsonSnapshot() {
        SimpleVectorStore original = (SimpleVectorStore) this.vectorStore;
        Path snapshot = this.temporaryDirectory.resolve("vector-store.json");
        original.save(snapshot.toFile());

        SimpleVectorStore restored = SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build();
        restored.load(snapshot.toFile());

        assertThat(restored.similaritySearch("spring")).extracting(Document::getText)
                .contains("spring framework");
    }

    private static final class DeterministicEmbeddingModel implements EmbeddingModel {

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
            return 2;
        }

        private static float[] vector(String text) {
            if (text.toLowerCase().contains("spring")) {
                return new float[] {1.0f, 0.0f};
            }
            if (text.toLowerCase().contains("vector")) {
                return new float[] {0.0f, 1.0f};
            }
            return new float[] {0.5f, 0.5f};
        }
    }
}
