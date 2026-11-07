package com.example.springai.pipeline;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EtlPreviewTests {
    @Test void previewDoesNotResolveModelsOrWriteAnIndex() {
        @SuppressWarnings("unchecked") ObjectProvider<VectorStore> stores = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<ChatModel> models = mock(ObjectProvider.class);
        var resource = resource("本地文档预览。检查读取、分块与元数据，不写入索引。", "preview.txt");
        var service = new EtlPipelineService(stores, models, resource, resource, resource);
        var result = new EtlPipelineController(service).preview("text");
        assertThat(result.get("loaded")).isEqualTo(false);
        assertThat(result.get("pipeline")).isEqualTo(java.util.List.of("Extract", "Transform"));
        assertThat(service.loadedDocumentCount()).isZero();
        verifyNoInteractions(stores, models);
    }

    @Test void unknownFormatCannotFallBackToAnotherReader() {
        @SuppressWarnings("unchecked") ObjectProvider<VectorStore> stores = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<ChatModel> models = mock(ObjectProvider.class);
        var resource = resource("文本", "preview.txt");
        var service = new EtlPipelineService(stores, models, resource, resource, resource);
        assertThatThrownBy(() -> service.preview("file:///sensitive.txt"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("仅支持");
        verifyNoInteractions(stores, models);
    }

    private static ByteArrayResource resource(String text, String name) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return name; }
        };
    }
}
