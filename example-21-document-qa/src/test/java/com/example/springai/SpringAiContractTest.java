package com.example.springai;

import java.util.List;

import com.example.springai.model.DeterministicEmbeddingModel;
import com.example.springai.reader.RequestDocumentReader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiContractTest {

    @Test
    void readerSplitterAndVectorStoreUseOfficialFunctionalContracts() {
        DocumentReader reader = new RequestDocumentReader(
                "契约文档",
                "Spring AI ETL Pipeline 由 DocumentReader、DocumentTransformer 和 DocumentWriter 三个核心契约组成。",
                "contract://etl",
                "spring-ai");
        TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(100)
            .withMinChunkSizeChars(20)
            .build();
        var vectorStore = SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build();

        List<Document> documents = splitter.transform(reader.read());
        vectorStore.write(documents);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("DocumentReader ETL").topK(2).build());

        assertThat(documents).singleElement();
        assertThat(documents.get(0).getMetadata())
            .containsEntry("parent_document_id", reader.read().get(0).getId())
            .containsEntry("chunk_index", 0)
            .containsEntry("total_chunks", 1);
        assertThat(results).singleElement().satisfies(document -> assertThat(document.getScore()).isNotNull());
    }

    @Test
    void questionAnswerAdvisorPublishesStableContextKeys() {
        var vectorStore = SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build();
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder().topK(4).similarityThreshold(0.0).build())
            .build();

        assertThat(advisor.getOrder()).isZero();
        assertThat(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS).isEqualTo("qa_retrieved_documents");
        assertThat(QuestionAnswerAdvisor.FILTER_EXPRESSION).isEqualTo("qa_filter_expression");
    }

}
