package com.example.springai.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class EtlPipelineServiceTest {

    private VectorStore vectorStore;

    private EtlPipelineService service;

    @BeforeEach
    void setUp() {
        this.vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(this.vectorStore);
        when(chatModelProvider.getIfAvailable()).thenReturn(null);
        when(this.vectorStore.getName()).thenReturn("MockVectorStore");

        this.service = new EtlPipelineService(
                vectorStoreProvider,
                chatModelProvider,
                resource("Spring AI ETL 测试文本。".repeat(30), "sample.txt"),
                resource("[{\"title\":\"标题\",\"content\":\"正文\"}]", "sample.json"),
                resource("# 标题\n\n正文", "sample.md")
        );
    }

    @Test
    void processWritesTransformedDocumentsAndTracksTheirIds() {
        EtlPipelineService.PipelineRun run = this.service.process("text", false, 0);

        assertThat(run.extractedDocumentCount()).isEqualTo(1);
        assertThat(run.transformedDocumentCount()).isPositive();
        assertThat(run.enrichedDocumentCount()).isZero();
        assertThat(this.service.loadedDocumentCount()).isEqualTo(run.transformedDocumentCount());
        verify(this.vectorStore).write(anyList());
    }

    @Test
    void clearDeletesExactlyTheTrackedDocumentIds() {
        EtlPipelineService.PipelineRun run = this.service.process("text", false, 0);
        int deleted = this.service.clearLoadedDocuments();

        ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass(List.class);
        verify(this.vectorStore).delete(ids.capture());
        assertThat(deleted).isEqualTo(run.transformedDocumentCount());
        assertThat(ids.getValue()).containsExactlyInAnyOrderElementsOf(
                run.documents().stream().map(Document::getId).toList());
        assertThat(this.service.loadedDocumentCount()).isZero();
    }

    @Test
    void clearKeepsTrackingWhenVectorStoreDeleteFails() {
        this.service.process("text", false, 0);
        doThrow(new IllegalStateException("delete failed")).when(this.vectorStore).delete(anyList());

        assertThatThrownBy(() -> this.service.clearLoadedDocuments())
                .isInstanceOf(IllegalStateException.class);
        assertThat(this.service.loadedDocumentCount()).isPositive();
    }

    @Test
    void enrichmentRejectsAnEmptyChatResponseBeforeVectorWrite() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(this.vectorStore);

        EtlPipelineService enrichedService = new EtlPipelineService(
                vectorStoreProvider,
                chatModelProvider,
                resource("Spring AI ETL 测试文本。".repeat(30), "sample.txt"),
                resource("[{\"title\":\"标题\",\"content\":\"正文\"}]", "sample.json"),
                resource("# 标题\n\n正文", "sample.md")
        );

        assertThatThrownBy(() -> enrichedService.process("text", true, 1))
                .isInstanceOf(com.example.springai.support.EtlOperationException.class)
                .hasMessageContaining("关键词增强未返回有效内容");
        verify(this.vectorStore, never()).write(anyList());
    }

    @Test
    void unknownFormatIsRejectedInsteadOfSilentlyFallingBackToText() {
        assertThatThrownBy(() -> this.service.process("xml", false, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text、json 或 markdown");
    }

    private ByteArrayResource resource(String content, String description) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), description);
    }
}
