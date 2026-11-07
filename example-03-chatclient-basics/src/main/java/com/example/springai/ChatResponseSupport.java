package com.example.springai;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;

/**
 * 安全读取 ChatClient 返回值，避免把可空返回类型当成必然存在。
 */
final class ChatResponseSupport {

    private ChatResponseSupport() {
    }

    static ChatResponse requireResponse(ChatClientResponse clientResponse) {
        if (clientResponse == null || clientResponse.chatResponse() == null) {
            throw new EmptyChatResponseException("模型未返回 ChatResponse");
        }
        return clientResponse.chatResponse();
    }

    static ChatResponse requireResponse(ChatResponse response) {
        if (response == null) {
            throw new EmptyChatResponseException("模型未返回 ChatResponse");
        }
        return response;
    }

    static String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new EmptyChatResponseException("模型未返回可用的文本内容");
        }
        return content;
    }

    static String requireContent(ChatResponse response) {
        ChatResponse actualResponse = requireResponse(response);
        Generation generation = actualResponse.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new EmptyChatResponseException("模型响应中没有生成结果");
        }
        return requireContent(generation.getOutput().getText());
    }

    static Map<String, Object> usageSummary(ChatResponse response) {
        Usage usage = requireResponse(response).getMetadata().getUsage();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("promptTokens", usage != null ? usage.getPromptTokens() : null);
        result.put("completionTokens", usage != null ? usage.getCompletionTokens() : null);
        result.put("totalTokens", usage != null ? usage.getTotalTokens() : null);
        result.put("cacheReadInputTokens", usage != null ? usage.getCacheReadInputTokens() : null);
        result.put("cacheWriteInputTokens", usage != null ? usage.getCacheWriteInputTokens() : null);
        return result;
    }

    static Map<String, Object> responseSummary(ChatResponse response) {
        ChatResponse actualResponse = requireResponse(response);
        Generation generation = actualResponse.getResult();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", requireContent(actualResponse));
        result.put("model", actualResponse.getMetadata().getModel());
        result.put("finishReason", generation != null ? generation.getMetadata().getFinishReason() : null);
        result.put("usage", usageSummary(actualResponse));
        return result;
    }

}
