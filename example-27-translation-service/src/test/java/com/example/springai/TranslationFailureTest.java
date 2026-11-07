package com.example.springai;

import java.util.List;

import com.example.springai.model.TranslationModels.GlossaryEntry;
import com.example.springai.model.TranslationModels.TranslationRequest;
import com.example.springai.service.TranslationService;
import com.example.springai.support.TranslationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationFailureTest {

    @Test
    void modelFailureIsWrappedAs502() {
        ChatModel failingModel = prompt -> {
            throw new IllegalStateException("供应商内部错误");
        };
        TranslationService service = new TranslationService(ChatClient.builder(failingModel).build());

        assertThatThrownBy(() -> service.translate(new TranslationRequest(
                "Hello", "EN", "ZH", null)))
            .isInstanceOf(TranslationException.class)
            .hasMessage("翻译模型调用失败");
    }

    @Test
    void emptyTranslatedTextTriggers502() {
        ChatModel emptyModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"translatedText":"","glossaryHits":[],"notes":""}
                """))));
        TranslationService service = new TranslationService(ChatClient.builder(emptyModel).build());

        assertThatThrownBy(() -> service.translate(new TranslationRequest(
                "Hello", "EN", "ZH", List.of(new GlossaryEntry("Hi", "你好")))))
            .isInstanceOf(TranslationException.class)
            .hasMessageContaining("翻译结果为空");
    }

    @Test
    void incompleteGlossaryHitTriggers502() {
        ChatModel incompleteModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"translatedText":"你好","glossaryHits":[{"source":"","target":"你好"}],"notes":""}
                """))));
        TranslationService service = new TranslationService(ChatClient.builder(incompleteModel).build());

        assertThatThrownBy(() -> service.translate(new TranslationRequest(
                "Hello", "EN", "ZH", null)))
            .isInstanceOf(TranslationException.class)
            .hasMessageContaining("术语命中字段不完整");
    }

}
