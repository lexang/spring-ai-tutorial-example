package com.example.springai;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;

/**
 * 集中处理 Spring AI 2.0 的可空响应和 Usage，避免每个端点重复写不安全的链式访问。
 */
final class TemplateResponseSupport {

    private TemplateResponseSupport() {
    }

    static String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new EmptyTemplateResponseException("模型未返回可用的文本内容");
        }
        return content;
    }

    static ChatResponse requireResponse(ChatResponse response) {
        if (response == null) {
            throw new EmptyTemplateResponseException("模型未返回 ChatResponse");
        }
        return response;
    }

    static String requireContent(ChatResponse response) {
        ChatResponse actualResponse = requireResponse(response);
        Generation generation = actualResponse.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new EmptyTemplateResponseException("模型响应中没有生成结果");
        }
        return requireContent(generation.getOutput().getText());
    }

    static Map<String, Object> usageSummary(ChatResponse response) {
        Usage usage = requireResponse(response).getMetadata().getUsage();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("promptTokens", usage != null ? usage.getPromptTokens() : null);
        summary.put("completionTokens", usage != null ? usage.getCompletionTokens() : null);
        summary.put("totalTokens", usage != null ? usage.getTotalTokens() : null);
        summary.put("cacheReadInputTokens", usage != null ? usage.getCacheReadInputTokens() : null);
        summary.put("cacheWriteInputTokens", usage != null ? usage.getCacheWriteInputTokens() : null);
        return summary;
    }

}
