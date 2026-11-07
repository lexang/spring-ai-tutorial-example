package com.example.springai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EmbeddingControllerValidationTests {

    private EmbeddingController controller;

    @BeforeEach
    void setUp() {
        this.controller = new EmbeddingController(mock(EmbeddingModel.class), mock(DocumentSearchService.class));
    }

    @Test
    void shouldRejectBlankBatchText() {
        assertThatThrownBy(() -> controller.embedBatch(List.of("有效文本", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文本不能为空");
    }

    @Test
    void shouldRejectNonIntegerTopK() {
        assertThatThrownBy(() -> controller.findSimilar(Map.of("content", "Java", "topK", "3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK 必须是整数");
    }

    @Test
    void shouldRejectNonPositiveTopK() {
        assertThatThrownBy(() -> controller.findSimilar(Map.of("content", "Java", "topK", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK 必须大于 0");
    }
}
