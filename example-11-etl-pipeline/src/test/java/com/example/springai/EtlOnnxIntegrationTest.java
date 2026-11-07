package com.example.springai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=transformers"
})
@EnabledIfEnvironmentVariable(named = "RUN_ETL_INTEGRATION_TESTS", matches = "true")
class EtlOnnxIntegrationTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void onnxEmbeddingCanWriteAndSearchVectorStore() {
        Document document = Document.builder()
                .text("Spring AI ETL 负责读取、转换和加载文档。")
                .metadata("test", "etl-integration")
                .build();

        this.vectorStore.write(List.of(document));
        List<Document> results = this.vectorStore.similaritySearch(SearchRequest.builder()
                .query("如何准备文档数据")
                .topK(1)
                .build());

        assertThat(results).isNotEmpty();
        this.vectorStore.delete(List.of(document.getId()));
    }
}
