package com.example.springai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceValidationTests {

    private VectorStore vectorStore;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        this.vectorStore = org.mockito.Mockito.mock(VectorStore.class);
        this.documentService = new DocumentService(this.vectorStore);
    }

    @Test
    void shouldRejectBlankQuery() {
        assertThatThrownBy(() -> this.documentService.search(" ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("搜索查询不能为空");
    }

    @Test
    void shouldRejectOutOfRangeTopK() {
        assertThatThrownBy(() -> this.documentService.search("向量", 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    @Test
    void shouldRejectNestedMetadata() {
        assertThatThrownBy(() -> this.documentService.addDocument(
                "文档", Map.of("tags", List.of("spring", "ai"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("简单标量");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAddValidatedMetadataWithoutMutatingCallerMap() {
        Map<String, Object> metadata = Map.of("category", "Spring AI", "year", 2026);

        String id = this.documentService.addDocument("文档内容", metadata);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(this.vectorStore).add(captor.capture());
        Document document = captor.getValue().get(0);
        assertThat(id).isEqualTo(document.getId());
        assertThat(document.getMetadata())
                .containsEntry("category", "Spring AI")
                .containsEntry("year", 2026)
                .containsKey("createdAt");
        assertThat(metadata).doesNotContainKey("createdAt");
    }

    @Test
    void shouldBuildSearchRequestWithOfficialDefaultTopK() {
        when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        this.documentService.search("向量", null);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(this.vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
        assertThat(captor.getValue().getSimilarityThreshold()).isZero();
    }

    @Test
    void shouldParseDeleteFilterBeforeCallingVectorStore() {
        this.documentService.deleteByFilter("category == 'Spring AI'");

        ArgumentCaptor<Filter.Expression> captor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(this.vectorStore).delete(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void shouldKeepBackendDeleteFailureAsServerError() {
        doThrow(new IllegalStateException("数据库不可用"))
                .when(this.vectorStore).delete(any(Filter.Expression.class));

        assertThatThrownBy(() -> this.documentService.deleteByFilter("category == 'Spring AI'"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据库不可用");
    }
}
