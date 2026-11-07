package com.example.springai.support;

import java.util.List;
import java.util.Map;

import com.example.springai.part1.KnowledgeRagController;
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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeRagResponseTests {
    private static final String KEY = QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS;

    @Test void preservesRetrievedIdsAndTreatsThemAsCandidatesNotVerifiedCitations() {
        Document doc = Document.builder().id("demo-1").text("演示资料").metadata(Map.of("source", "tutorial-demo"))
                .score(0.8).build();
        var result = RagResultMapper.from(response("模型的回答", List.of(doc)), KEY);
        assertThat(result.retrievedSources()).singleElement().extracting(RagResultMapper.RetrievedSource::id)
                .isEqualTo("demo-1");
        assertThat(result.answer()).isEqualTo("模型的回答");
        assertThat(result.answerUsage().totalTokens()).isNull();
    }

    @Test void noContextSuppressesUngroundedAnswerWithoutClaimingNoModelCall() {
        var result = RagResultMapper.from(response("我凭自身知识回答了", List.of()), KEY);
        assertThat(result.status()).isEqualTo("NO_CONTEXT");
        assertThat(result.answer()).doesNotContain("自身知识");
        assertThat(result.retrievedSources()).isEmpty();
    }

    @Test void absentResponseAndMalformedContextAreFailures() {
        assertThatThrownBy(() -> RagResultMapper.from(null, KEY)).isInstanceOf(RagUpstreamException.class);
        assertThatThrownBy(() -> RagResultMapper.from(response("回答", "非列表"), KEY))
                .isInstanceOf(RagUpstreamException.class);
        assertThatThrownBy(() -> RagResultMapper.from(response("回答", List.of("非文档")), KEY))
                .isInstanceOf(RagUpstreamException.class);
    }

    @Test void usesTheActualAdvisorContextFromTheSameRetrievalCall() {
        VectorStore store = mock(VectorStore.class);
        Document doc = Document.builder().id("doc-1").text("Spring AI 的 {json} 是普通资料文本。")
                .metadata(Map.of("product", "Spring AI")).build();
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        RecordingModel model = new RecordingModel();
        var controller = new KnowledgeRagController(ChatClient.builder(model), store);
        var answer = controller.ask(new KnowledgeRagController.QuestionInput("如何使用 {json}？", "Spring AI", null));
        assertThat(answer.retrievedSources()).singleElement().extracting(RagResultMapper.RetrievedSource::id)
                .isEqualTo("doc-1");
        assertThat(model.lastPrompt.getUserMessage().getText()).contains("{json}", doc.getText());
        verify(store, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test void httpInputAndModelFailuresRemainSeparate() throws Exception {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("演示资料")));
        RecordingModel model = new RecordingModel();
        var controller = new KnowledgeRagController(ChatClient.builder(model), store);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\" \"]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\" \"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
        model.failure = new IllegalArgumentException("下游机密正文");
        String body = mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"有效问题\"}")).andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("下游机密正文");
    }

    private static ChatClientResponse response(String answer, Object context) {
        return ChatClientResponse.builder().chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(answer)))))
                .context(Map.of(KEY, context)).build();
    }

    static class RecordingModel implements ChatModel {
        Prompt lastPrompt;
        RuntimeException failure;
        @Override public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            if (failure != null) { throw failure; }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("根据演示资料回答"))));
        }
    }
}
