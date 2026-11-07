package com.example.springai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputSupportTest {

    @Test
    void requiresNonEmptyCollections() {
        assertThat(StructuredOutputResponseSupport.requireNonEmpty(List.of("a"), "列表"))
                .containsExactly("a");

        assertThatThrownBy(() -> StructuredOutputResponseSupport.requireNonEmpty(List.of(), "列表"))
                .isInstanceOf(InvalidStructuredOutputResponseException.class)
                .hasMessage("列表为空列表");
    }

    @Test
    void rejectsResponseEntityWithMissingResponseOrEntity() {
        ResponseEntity<org.springframework.ai.chat.model.ChatResponse, Models.Person> missingResponse =
                new ResponseEntity<>(null, new Models.Person("张三", 25, "工程师"));
        assertThatThrownBy(() -> StructuredOutputResponseSupport.requireResponseEntity(missingResponse, "人员"))
                .isInstanceOf(InvalidStructuredOutputResponseException.class)
                .hasMessage("模型未返回 ChatResponse");

        ResponseEntity<org.springframework.ai.chat.model.ChatResponse, Models.Person> missingEntity =
                new ResponseEntity<>(new org.springframework.ai.chat.model.ChatResponse(List.of()), null);
        assertThatThrownBy(() -> StructuredOutputResponseSupport.requireResponseEntity(missingEntity, "人员"))
                .isInstanceOf(InvalidStructuredOutputResponseException.class)
                .hasMessage("人员实体为空");
    }

    @Test
    void trimsAndValidatesRequestText() {
        assertThat(StructuredOutputRequestValidation.requireText("  张三  ", "text"))
                .isEqualTo("张三");
        assertThatThrownBy(() -> StructuredOutputRequestValidation.requireText(" ", "text"))
                .isInstanceOf(InvalidStructuredOutputRequestException.class)
                .hasMessage("text不能为空");
    }

}
