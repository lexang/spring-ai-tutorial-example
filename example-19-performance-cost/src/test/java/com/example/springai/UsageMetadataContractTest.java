package com.example.springai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.springai.config.CostPolicyProperties;
import com.example.springai.service.PerformanceService;
import com.example.springai.service.ResponseMetadataSupport;
import com.example.springai.web.ApiExceptionHandler;
import com.example.springai.web.PerformanceController;
import com.openai.models.completions.CompletionUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsageMetadataContractTest {

    @Test
    void incompleteCustomUsageDoesNotProduceCostAndLimitsRejectBeforeCalling() {
        var incomplete = new org.springframework.ai.chat.metadata.Usage() {
            public Integer getPromptTokens() { return 5; }
            public Integer getCompletionTokens() { return null; }
            public Integer getTotalTokens() { return 5; }
            public Object getNativeUsage() { return null; }
        };
        var result = service(prompt -> response(ChatResponseMetadata.builder().usage(incomplete).build()))
            .chat("问题", 20, null);
        assertThat(result.completionTokens()).isNull();
        assertThat(result.actualCost()).isNull();
        AtomicInteger calls = new AtomicInteger();
        var limited = service(prompt -> { calls.incrementAndGet(); return null; });
        assertThatThrownBy(() -> limited.chat("问题", 8193, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limited.chat("字".repeat(12001), 20, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void emptyMetadataIsUnknownRatherThanZeroCostOrNativeUsage() {
        ChatResponse response = response(ChatResponseMetadata.builder().build());
        assertThat(response.getMetadata().getUsage()).isInstanceOf(EmptyUsage.class);
        assertThat(response.getMetadata().getUsage().getNativeUsage()).isEqualTo(Map.of());
        assertThat(response.getMetadata().getRateLimit()).isInstanceOf(EmptyRateLimit.class);
        PerformanceService.ChatResult result = service(prompt -> response).chat("问题", 20, null);
        assertThat(result.metadata().usageReported()).isFalse();
        assertThat(result.promptTokens()).isNull();
        assertThat(result.actualCost()).isNull();
        assertThat(result.nativeUsagePresent()).isFalse();
        assertThat(result.metadata().rateLimits().reported()).isFalse();
        assertThat(result.metadata().rateLimits().tokensRemaining()).isNull();
        assertThat(service(prompt -> response).cumulativeUsage().cumulativeNativeUsagePresent()).isFalse();
    }

    @Test
    void providerZeroUsageAndExhaustedRateLimitRemainReported() {
        RateLimit limits = new RateLimit() {
            public Long getRequestsLimit() { return 100L; }
            public Long getRequestsRemaining() { return 0L; }
            public Duration getRequestsReset() { return Duration.ofSeconds(30); }
            public Long getTokensLimit() { return 1000L; }
            public Long getTokensRemaining() { return 0L; }
            public Duration getTokensReset() { return Duration.ofSeconds(20); }
        };
        ChatResponse response = response(ChatResponseMetadata.builder().id("response-008").model("fixture")
            .usage(new DefaultUsage(0, 0)).rateLimit(limits)
            .promptMetadata(PromptMetadata.of(PromptMetadata.PromptFilterMetadata.from(2, Map.of("filtered", false))))
            .build());
        PerformanceService.ChatResult result = service(prompt -> response).chat("问题", 20, null);
        assertThat(result.metadata().usageReported()).isTrue();
        assertThat(result.actualCost()).isZero();
        assertThat(result.metadata().responseId()).isEqualTo("response-008");
        assertThat(result.metadata().model()).isEqualTo("fixture");
        assertThat(result.metadata().rateLimits().reported()).isTrue();
        assertThat(result.metadata().rateLimits().requestsRemaining()).isZero();
        assertThat(result.metadata().rateLimits().tokensReset()).isEqualTo(Duration.ofSeconds(20));
        assertThat(result.metadata().promptFilterIndexes()).containsExactly(2);
    }

    @Test
    void openAiNativeUsageDetailsMatchReleasedSdkAndAreOptional() {
        CompletionUsage usage = CompletionUsage.builder().promptTokens(20).completionTokens(10).totalTokens(30)
            .promptTokensDetails(CompletionUsage.PromptTokensDetails.builder().audioTokens(2).cachedTokens(3).build())
            .completionTokensDetails(CompletionUsage.CompletionTokensDetails.builder().reasoningTokens(4)
                .acceptedPredictionTokens(5).audioTokens(6).rejectedPredictionTokens(7).build()).build();
        var details = ResponseMetadataSupport.openAiDetails(usage);
        assertThat(details).isEqualTo(new ResponseMetadataSupport.OpenAiDetails(2L, 3L, 4L, 5L, 6L, 7L));
        assertThat(ResponseMetadataSupport.openAiDetails(Map.of())).isNull();
        assertThat(ResponseMetadataSupport.openAiDetails(CompletionUsage.builder()
            .promptTokens(1).completionTokens(2).totalTokens(3).build()).cachedTokens()).isNull();
    }

    @Test
    void accumulationAddsCacheMetricsAndPreservesOtherMetadataButDropsMergedNativeUsage() {
        ChatResponse first = response(ChatResponseMetadata.builder()
            .usage(new DefaultUsage(10, 5, 15, Map.of("round", 1), 3L, null)).build());
        ChatResponse second = response(ChatResponseMetadata.builder().id("second").model("fixture")
            .keyValue("custom", "preserved")
            .usage(new DefaultUsage(20, 10, 30, Map.of("round", 2), 4L, 2L)).build());
        ChatResponse total = UsageCalculator.accumulateResponseUsage(second, first);
        assertThat(total.getMetadata().getUsage().getTotalTokens()).isEqualTo(45);
        assertThat(total.getMetadata().getUsage().getCacheReadInputTokens()).isEqualTo(7L);
        assertThat(total.getMetadata().getUsage().getCacheWriteInputTokens()).isEqualTo(2L);
        assertThat(total.getMetadata().getUsage().getNativeUsage()).isNull();
        assertThat(total.getMetadata().getId()).isEqualTo("second");
        assertThat((Object) total.getMetadata().get("custom")).isEqualTo("preserved");
        assertThat(UsageCalculator.accumulateResponseUsage(null, total)).isSameAs(total);
        assertThat(UsageCalculator.getCumulativeUsage(new EmptyUsage(), first).getNativeUsage())
            .isEqualTo(Map.of("round", 1));
    }

    @Test
    void partialCumulativeUsageIsMarkedIncompleteAndBudgetRejectsWithoutCallingModel() {
        AtomicInteger calls = new AtomicInteger();
        PerformanceService service = service(prompt -> {
            int round = calls.incrementAndGet();
            return response(round == 1 ? ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).build()
                    : ChatResponseMetadata.builder().build());
        });
        var usage = service.cumulativeUsage();
        assertThat(usage.usageComplete()).isFalse();
        assertThat(usage.cumulativeTotalTokens()).isEqualTo(15);
        calls.set(0);
        assertThatThrownBy(() -> service.chat("问题", 20, java.math.BigDecimal.ZERO))
            .isInstanceOf(PerformanceService.BudgetExceededException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void missingResponseAndMalformedJsonBecomeChinese502And400() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PerformanceController(service(prompt -> null)))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/performance/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"问题\",\"maxOutputTokens\":20}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("message").value("AI 服务未返回可用结果，请稍后重试"));
        mvc.perform(post("/api/performance/chat").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("message").value("请求 JSON 格式不正确"));
        assertThatThrownBy(() -> service(prompt -> new ChatResponse(List.of())).chat("问题", 20, null))
            .isInstanceOf(PerformanceService.ModelOperationException.class);
    }

    private static PerformanceService service(ChatModel model) {
        return new PerformanceService(ChatClient.create(model), new CostPolicyProperties());
    }

    private static ChatResponse response(ChatResponseMetadata metadata) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("固定回答"))), metadata);
    }

}
