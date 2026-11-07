package com.example.springai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QAServiceTests {

    private VectorStore vectorStore;

    private ChatClient chatClient;

    private ChatClient.CallResponseSpec callResponseSpec;

    private QAService qaService;

    @BeforeEach
    void setUp() {
        this.vectorStore = mock(VectorStore.class);
        this.chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        this.callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(this.chatClient);
        when(this.chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(this.callResponseSpec);
        this.qaService = new QAService(this.vectorStore, builder);
    }

    @Test
    void shouldNotCallModelWhenNoTextContextExists() {
        when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        QAService.QAResponse response = this.qaService.askQuestion("什么是向量数据库");

        assertThat(response.answer()).contains("没有找到");
        assertThat(response.sources()).isEmpty();
        verify(this.chatClient, never()).prompt(anyString());
    }

    @Test
    void shouldRejectNullModelContent() {
        Document document = Document.builder()
                .text("向量数据库保存向量嵌入。")
                .metadata(Map.of("category", "向量数据库"))
                .score(0.9)
                .build();
        when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document));
        when(this.callResponseSpec.content()).thenReturn(null);

        assertThatThrownBy(() -> this.qaService.askQuestion("什么是向量数据库"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("聊天模型未返回文本内容");
    }
}
