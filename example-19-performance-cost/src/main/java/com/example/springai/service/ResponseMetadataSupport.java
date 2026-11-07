package com.example.springai.service;

import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;

import com.openai.models.completions.CompletionUsage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 安全输出响应元数据；保留未报告与实际为零的区别，不直接序列化原生供应商对象。
 */
public final class ResponseMetadataSupport {

    private ResponseMetadataSupport() {
    }

    public static boolean usageReported(Usage usage) {
        return usage != null && !(usage instanceof EmptyUsage);
    }

    public static Snapshot snapshot(ChatResponse response) {
        if (response == null) {
            throw new PerformanceService.ModelOperationException("模型未返回 ChatResponse");
        }
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata.getUsage();
        RateLimit rateLimit = metadata.getRateLimit();
        boolean usageReported = usageReported(usage);
        boolean rateLimitReported = rateLimit != null && !(rateLimit instanceof EmptyRateLimit);
        RateLimits limits = new RateLimits(rateLimitReported,
                rateLimitReported ? rateLimit.getRequestsLimit() : null,
                rateLimitReported ? rateLimit.getRequestsRemaining() : null,
                rateLimitReported ? rateLimit.getRequestsReset() : null,
                rateLimitReported ? rateLimit.getTokensLimit() : null,
                rateLimitReported ? rateLimit.getTokensRemaining() : null,
                rateLimitReported ? rateLimit.getTokensReset() : null);
        List<Integer> filterIndexes = metadata.getPromptMetadata() == null ? List.of()
                : StreamSupport.stream(metadata.getPromptMetadata().spliterator(), false)
                    .map(filter -> filter.getPromptIndex()).toList();
        return new Snapshot(metadata.getId(), metadata.getModel(), usageReported, limits, filterIndexes,
                usageReported ? openAiDetails(usage.getNativeUsage()) : null);
    }

    public static OpenAiDetails openAiDetails(Object nativeUsage) {
        if (!(nativeUsage instanceof CompletionUsage usage)) {
            return null;
        }
        var prompt = usage.promptTokensDetails();
        var completion = usage.completionTokensDetails();
        return new OpenAiDetails(
            prompt.flatMap(CompletionUsage.PromptTokensDetails::audioTokens).orElse(null),
            prompt.flatMap(CompletionUsage.PromptTokensDetails::cachedTokens).orElse(null),
            completion.flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens).orElse(null),
            completion.flatMap(CompletionUsage.CompletionTokensDetails::acceptedPredictionTokens).orElse(null),
            completion.flatMap(CompletionUsage.CompletionTokensDetails::audioTokens).orElse(null),
            completion.flatMap(CompletionUsage.CompletionTokensDetails::rejectedPredictionTokens).orElse(null));
    }

    public record Snapshot(String responseId, String model, boolean usageReported, RateLimits rateLimits,
            List<Integer> promptFilterIndexes, OpenAiDetails openAiDetails) {
    }

    public record RateLimits(boolean reported, Long requestsLimit, Long requestsRemaining,
            Duration requestsReset, Long tokensLimit, Long tokensRemaining, Duration tokensReset) {
    }

    public record OpenAiDetails(Long promptAudioTokens, Long cachedTokens, Long reasoningTokens,
            Long acceptedPredictionTokens, Long completionAudioTokens, Long rejectedPredictionTokens) {
    }

}
