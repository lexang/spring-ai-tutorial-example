package com.example.springai;

import java.util.Collection;
import java.util.Map;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;

/**
 * 集中处理 Spring AI 2.0 结构化输出 API 的可空返回值。
 *
 * <p>ChatClient.entity()、content() 以及 ResponseEntity 的字段都可能为空；示例
 * 在进入业务逻辑前显式检查，便于把“模型没有返回结果”和“模型调用失败”区分开。</p>
 */
final class StructuredOutputResponseSupport {

    private StructuredOutputResponseSupport() {
    }

    static <T> T requireEntity(T entity, String description) {
        if (entity == null) {
            throw new InvalidStructuredOutputResponseException(description + "为空");
        }
        return entity;
    }

    static String requireText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new InvalidStructuredOutputResponseException("模型未返回可用的文本内容");
        }
        return text.trim();
    }

    static ChatResponse requireResponse(ChatResponse response) {
        if (response == null) {
            throw new InvalidStructuredOutputResponseException("模型未返回 ChatResponse");
        }
        return response;
    }

    static String requireText(ChatResponse response) {
        ChatResponse actualResponse = requireResponse(response);
        Generation generation = actualResponse.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new InvalidStructuredOutputResponseException("ChatResponse 中没有生成结果");
        }
        return requireText(generation.getOutput().getText());
    }

    static <E> ResponseEntity<ChatResponse, E> requireResponseEntity(
            ResponseEntity<ChatResponse, E> responseEntity, String description) {
        if (responseEntity == null) {
            throw new InvalidStructuredOutputResponseException(description + "响应为空");
        }
        requireResponse(responseEntity.response());
        requireEntity(responseEntity.entity(), description + "实体");
        return responseEntity;
    }

    static <T extends Collection<?>> T requireNonEmpty(T values, String description) {
        requireEntity(values, description);
        if (values.isEmpty()) {
            throw new InvalidStructuredOutputResponseException(description + "为空列表");
        }
        return values;
    }

    static <K, V, T extends Map<K, V>> T requireNonEmptyMap(T values, String description) {
        requireEntity(values, description);
        if (values.isEmpty()) {
            throw new InvalidStructuredOutputResponseException(description + "为空对象");
        }
        return values;
    }

}
