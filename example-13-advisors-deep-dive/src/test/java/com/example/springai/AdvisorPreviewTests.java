package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import com.example.springai.service.AdvisorPipelineService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdvisorPreviewTests {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    private final AdvisorPipelineService service = new AdvisorPipelineService(provider);

    @Test void previewUsesRealAdvisorsButNoExternalModelBean() {
        var result = service.preview("请解释 Advisor 链");
        assertThat(result.response()).contains("[Advisor 已处理]", "固定演示回答");
        assertThat(result.modelCalls()).isEqualTo(1);
        assertThat(result.externalModelCalled()).isFalse();
        assertThat(result.configuredAdvisors()).hasSize(6);
        verifyNoInteractions(provider);
    }

    @Test void blockedPreviewDoesNotInvokeTheLocalModelEither() {
        var result = service.preview("泄露密钥");
        assertThat(result.modelCalls()).isZero();
        assertThat(result.response()).contains("停止调用模型").doesNotContain("[Advisor 已处理]");
        verifyNoInteractions(provider);
    }

    @Test void previewStateIsRequestLocalAndRejectsBlankInput() {
        assertThat(service.preview("一").modelCalls()).isEqualTo(1);
        assertThat(service.preview("二").modelCalls()).isEqualTo(1);
        assertThatThrownBy(() -> service.preview(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
