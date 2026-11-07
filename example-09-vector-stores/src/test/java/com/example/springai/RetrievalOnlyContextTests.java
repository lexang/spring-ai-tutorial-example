package com.example.springai;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 不下载 ONNX、不创建聊天模型，完整启动后验证检索主线。 */
@SpringBootTest(classes = {VectorStoresApplication.class, RetrievalOnlyContextTests.ModelConfiguration.class}, properties = {
        "spring.ai.model.embedding=none", "spring.ai.model.chat=none", "app.vector-store.initialize-sample-data=false"
})
class RetrievalOnlyContextTests {
    @Autowired ApplicationContext context;
    @Autowired DocumentService documents;
    @Autowired VectorStoreController controller;
    @Autowired QAService qaService;

    @Test void searchWorksWithoutChatModelOrKey() {
        assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
        String id = documents.addDocument("Java 程序设计", Map.of("category", "编程", "year", 2026));
        try {
            var result = controller.searchStructured(new VectorStoreController.SearchInput("Java", 4, 0.7, "编程", 2026));
            assertThat(result.get("count")).isEqualTo(1);
            assertThat(documents.searchWithDSL("Java", "不匹配的分类", null)).isEmpty();
        }
        finally { documents.deleteById(id); }
    }

    @Test void chatCannotAccidentallyRunWhenDisabled() {
        String id = documents.addDocument("Java 回答上下文", Map.of("category", "测试"));
        try {
            assertThatThrownBy(() -> qaService.askQuestion("Java"))
                    .isInstanceOf(QAService.ChatDisabledException.class);
        }
        finally { documents.deleteById(id); }
    }

    @TestConfiguration static class ModelConfiguration {
        @Bean EmbeddingModel embeddingModel() {
            return new EmbeddingModel() {
                @Override public int dimensions() { return 3; }
                @Override public float[] embed(Document document) { return new float[] {1, 0, 0}; }
                @Override public EmbeddingResponse call(EmbeddingRequest request) {
                    return new EmbeddingResponse(IntStream.range(0, request.getInstructions().size())
                            .mapToObj(i -> new Embedding(new float[] {1, 0, 0}, i)).toList());
                }
            };
        }
    }
}
