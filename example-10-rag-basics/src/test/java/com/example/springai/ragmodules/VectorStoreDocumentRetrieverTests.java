package com.example.springai.ragmodules;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class VectorStoreDocumentRetrieverTests {

    @Test
    void shouldUseOfficialDefaultsAndOriginalQueryText() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .build();

        retriever.retrieve(new Query("什么是 RAG？"));

        assertThat(vectorStore.lastRequest.getQuery()).isEqualTo("什么是 RAG？");
        assertThat(vectorStore.lastRequest.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
        assertThat(vectorStore.lastRequest.getSimilarityThreshold())
                .isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
    }

    @Test
    void shouldUseRetrieverFilterWhenRequestHasNoOverride() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        Filter.Expression configuredFilter = new FilterExpressionBuilder()
                .eq("product", "Spring AI")
                .build();
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(configuredFilter)
                .build();

        retriever.retrieve(new Query("什么是 RAG？"));

        assertThat(vectorStore.lastRequest.getFilterExpression()).isEqualTo(configuredFilter);
    }

    @Test
    void shouldLetRequestFilterOverrideRetrieverFilter() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression configuredFilter = builder.eq("product", "Spring AI").build();
        Filter.Expression requestFilter = builder.eq("category", "RAG").build();
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(configuredFilter)
                .build();
        Query query = Query.builder()
                .text("什么是 RAG？")
                .context(Map.of(VectorStoreDocumentRetriever.FILTER_EXPRESSION, requestFilter))
                .build();

        retriever.retrieve(query);

        assertThat(vectorStore.lastRequest.getFilterExpression()).isEqualTo(requestFilter);
    }

    @Test
    void shouldParseTextFilterFromRequestContext() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .build();
        Query query = Query.builder()
                .text("什么是 RAG？")
                .context(Map.of(
                        VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        "category == 'RAG'"))
                .build();

        retriever.retrieve(query);

        Filter.Expression expected = new FilterExpressionBuilder()
                .eq("category", "RAG")
                .build();
        assertThat(vectorStore.lastRequest.getFilterExpression()).isEqualTo(expected);
    }

    @Test
    void shouldEvaluateSupplierForEveryRetrieval() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        AtomicInteger counter = new AtomicInteger();
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(() -> new FilterExpressionBuilder()
                        .eq("request", counter.incrementAndGet())
                        .build())
                .build();

        retriever.retrieve(new Query("第一次"));
        Filter.Expression first = vectorStore.lastRequest.getFilterExpression();
        retriever.retrieve(new Query("第二次"));
        Filter.Expression second = vectorStore.lastRequest.getFilterExpression();

        assertThat(counter).hasValue(2);
        assertThat(first).isNotEqualTo(second);
        assertThat(((Filter.Value) second.right()).value()).isEqualTo(2);
    }

    @Test
    void shouldRejectZeroTopK() {
        assertThatThrownBy(() -> VectorStoreDocumentRetriever.builder()
                .vectorStore(new RecordingVectorStore())
                .topK(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK must be greater than 0");
    }

    private static final class RecordingVectorStore implements VectorStore {

        private SearchRequest lastRequest;

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            this.lastRequest = request;
            return List.of();
        }

        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }
    }
}
