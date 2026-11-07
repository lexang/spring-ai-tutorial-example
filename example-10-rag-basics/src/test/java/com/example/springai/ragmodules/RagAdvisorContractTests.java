package com.example.springai.ragmodules;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.task.SyncTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class RagAdvisorContractTests {

    @Test
    void questionAnswerAdvisorShouldExposeRetrievedDocumentsInContextAndMetadata() {
        Document document = document("qa-doc", "QuestionAnswerAdvisor 会检索向量库。");
        RecordingVectorStore vectorStore = new RecordingVectorStore(List.of(document));
        RecordingChatModel chatModel = new RecordingChatModel("基础 RAG 回答");

        ChatClientResponse response = ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user("Spring AI 如何实现 RAG？")
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .call()
                .chatClientResponse();

        assertThat(vectorStore.lastRequest.getQuery()).isEqualTo("Spring AI 如何实现 RAG？");
        assertThat(chatModel.lastPrompt.getUserMessage().getText())
                .contains("Spring AI 如何实现 RAG？")
                .contains(document.getText());
        assertThat((List<?>) response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS))
                .isEqualTo(List.of(document));
        assertThat(response.chatResponse()).isNotNull();
        assertThat((List<?>) response.chatResponse().getMetadata()
                .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS))
                .isEqualTo(List.of(document));
    }

    @Test
    void retrievalAugmentationAdvisorShouldExposeProcessedDocumentContext() {
        Document document = document("modular-doc", "模块化 RAG 会增强原始问题。");
        RecordingChatModel chatModel = new RecordingChatModel("模块化 RAG 回答");
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(query -> List.of(document))
                .taskExecutor(new SyncTaskExecutor())
                .build();

        ChatClientResponse response = ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user("模块化 RAG 如何工作？")
                .advisors(advisor)
                .call()
                .chatClientResponse();

        assertThat(chatModel.lastPrompt.getUserMessage().getText())
                .contains("模块化 RAG 如何工作？")
                .contains(document.getText());
        assertThat((List<?>) response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT))
                .isEqualTo(List.of(document));
        assertThat(response.chatResponse()).isNotNull();
        assertThat((List<?>) response.chatResponse().getMetadata()
                .get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT))
                .isEqualTo(List.of(document));
    }

    private Document document(String id, String text) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of("source", "test"))
                .score(0.9)
                .build();
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String answer;

        private Prompt lastPrompt;

        private RecordingChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage(this.answer))));
        }
    }

    private static final class RecordingVectorStore implements VectorStore {

        private final List<Document> documents;

        private SearchRequest lastRequest;

        private RecordingVectorStore(List<Document> documents) {
            this.documents = documents;
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            this.lastRequest = request;
            return this.documents;
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
