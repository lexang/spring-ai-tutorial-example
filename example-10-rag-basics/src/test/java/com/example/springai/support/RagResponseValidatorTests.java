package com.example.springai.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagResponseValidatorTests {

    @Test
    void shouldReturnNonBlankContentUnchanged() {
        assertThat(RagResponseValidator.requireContent("  可用回答  "))
                .isEqualTo("  可用回答  ");
    }

    @Test
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> RagResponseValidator.requireContent(null))
                .isInstanceOf(RagUpstreamException.class)
                .hasMessage("模型未返回可用文本，请稍后重试");
    }

    @Test
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> RagResponseValidator.requireContent(" \n\t"))
                .isInstanceOf(RagUpstreamException.class)
                .hasMessage("模型未返回可用文本，请稍后重试");
    }
}
