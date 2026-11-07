package com.example.springai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;

import com.knuddels.jtokkit.api.EncodingType;
import com.example.springai.config.CostPolicyProperties;

/**
 * 把 Spring AI 的使用量转换为应用可执行的预算和批处理策略。
 */
@Service
public class PerformanceService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final ChatClient chatClient;

    private final CostPolicyProperties costPolicy;

    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

    public PerformanceService(ChatClient chatClient, CostPolicyProperties costPolicy) {
        this.chatClient = chatClient;
        this.costPolicy = costPolicy;
    }

    public Estimate estimate(String text, int maxOutputTokens) {
        requireText(text);
        if (maxOutputTokens < 1 || maxOutputTokens > 8192) {
            throw new IllegalArgumentException("maxOutputTokens 必须位于 1 到 8192 之间");
        }
        int inputTokens = this.tokenCountEstimator.estimate(text);
        BigDecimal estimatedCost = cost(inputTokens, maxOutputTokens, null, null);
        return new Estimate(inputTokens, maxOutputTokens, estimatedCost, ratesAreConfigured());
    }

    public ChatResult chat(String prompt, int maxOutputTokens, BigDecimal maxBudget) {
        if (maxBudget != null && maxBudget.signum() < 0) {
            throw new IllegalArgumentException("maxBudget 不能为负数");
        }
        Estimate estimate = estimate(prompt, maxOutputTokens);
        if (maxBudget != null && estimate.estimatedCost().compareTo(maxBudget) > 0) {
            throw new BudgetExceededException(
                "请求预估成本 " + estimate.estimatedCost() + " 已超过预算 " + maxBudget);
        }

        ChatResponse response = call(prompt, maxOutputTokens);
        if (response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null
                || response.getResult().getOutput().getText().isBlank()) {
            throw new ModelOperationException("模型未返回非空文本回答");
        }
        Usage usage = response.getMetadata().getUsage();
        boolean usageReported = ResponseMetadataSupport.usageReported(usage);
        Integer promptTokens = usageReported ? usage.getPromptTokens() : null;
        Integer completionTokens = usageReported ? usage.getCompletionTokens() : null;
        BigDecimal actualCost = usageReported && promptTokens != null && completionTokens != null
            ? cost(promptTokens, completionTokens,
            usage.getCacheReadInputTokens(), usage.getCacheWriteInputTokens()) : null;
        return new ChatResult(response.getResult().getOutput().getText(), promptTokens,
            completionTokens, usageReported ? totalTokens(usage) : null, actualCost,
            usageReported && usage.getNativeUsage() != null,
            usageReported ? usage.getCacheReadInputTokens() : null,
            usageReported ? usage.getCacheWriteInputTokens() : null,
            ResponseMetadataSupport.snapshot(response));
    }

    private ChatResponse call(String prompt, int maxOutputTokens) {
        try {
            ChatResponse response = this.chatClient.prompt(prompt)
                // 2.0 的 ChatClient.options() 接收 Builder，而不是已 build 的 ChatOptions。
                .options(ChatOptions.builder().maxTokens(maxOutputTokens))
                .call()
                .chatResponse();
            if (response == null) {
                throw new ModelOperationException("模型未返回 ChatResponse");
            }
            return response;
        }
        catch (ModelOperationException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new ModelOperationException("模型调用失败", exception);
        }
    }

    public UsageReport cumulativeUsage() {
        ChatResponse first = call("第一步：读取固定背景。", 40);
        ChatResponse second = call("第二步：根据背景给出摘要。", 40);
        Usage firstUsage = first.getMetadata().getUsage();
        Usage secondUsage = second.getMetadata().getUsage();
        Usage cumulative = UsageCalculator.getCumulativeUsage(
            secondUsage, first);
        return new UsageReport(
            tokenCount(firstUsage == null ? null : firstUsage.getPromptTokens()),
            tokenCount(secondUsage == null ? null : secondUsage.getPromptTokens()),
            tokenCount(cumulative == null ? null : cumulative.getPromptTokens()),
            tokenCount(cumulative == null ? null : cumulative.getCompletionTokens()),
            tokenCount(cumulative),
            ResponseMetadataSupport.usageReported(cumulative) && cumulative.getNativeUsage() != null,
            ResponseMetadataSupport.usageReported(first.getMetadata().getUsage())
                && ResponseMetadataSupport.usageReported(second.getMetadata().getUsage()));
    }

    public BatchPlan batch(List<String> texts, int maxInputTokens, double reservePercentage) {
        if (texts == null || texts.isEmpty() || texts.size() > 100) {
            throw new IllegalArgumentException("documents 必须包含 1 到 100 项");
        }
        texts.forEach(this::requireText);
        if (maxInputTokens < 1) {
            throw new IllegalArgumentException("maxInputTokens 必须大于 0");
        }
        if (!Double.isFinite(reservePercentage) || reservePercentage < 0 || reservePercentage >= 1) {
            throw new IllegalArgumentException("reservePercentage 必须位于 [0, 1) 区间");
        }

        List<Document> documents = texts.stream().map(Document::new).toList();
        BatchingStrategy strategy = new TokenCountBatchingStrategy(
            EncodingType.CL100K_BASE, maxInputTokens, reservePercentage);
        List<List<Document>> batches = strategy.batch(documents);
        List<Batch> result = batches.stream()
            .map(batch -> new Batch(
                batch.size(),
                batch.stream()
                    .map(document -> this.tokenCountEstimator.estimate(document.getText()))
                    .toList(),
                batch.stream()
                    .mapToInt(document -> this.tokenCountEstimator.estimate(document.getText()))
                    .sum()))
            .toList();
        return new BatchPlan(maxInputTokens, reservePercentage, result);
    }

    private BigDecimal cost(int inputTokens, int outputTokens,
            Long cacheReadInputTokens, Long cacheWriteInputTokens) {
        BigDecimal inputRate = nonNegative(this.costPolicy.getInputPerMillion());
        BigDecimal outputRate = nonNegative(this.costPolicy.getOutputPerMillion());
        BigDecimal cacheReadRate = nonNegative(this.costPolicy.getCacheReadPerMillion());
        BigDecimal cacheWriteRate = nonNegative(this.costPolicy.getCacheWritePerMillion());
        long cacheReadTokens = cacheReadInputTokens == null ? 0 : cacheReadInputTokens;
        long cacheWriteTokens = cacheWriteInputTokens == null ? 0 : cacheWriteInputTokens;
        // 2.0.2 各供应商的 promptTokens 缓存语义不同，不能一律相减。
        long cachedInputTokens = this.costPolicy.isPromptTokensIncludeCache()
            ? Math.min(inputTokens, cacheReadTokens + cacheWriteTokens) : 0;
        long regularInputTokens = inputTokens - cachedInputTokens;
        BigDecimal input = inputRate.multiply(BigDecimal.valueOf(regularInputTokens));
        BigDecimal output = outputRate.multiply(BigDecimal.valueOf(outputTokens));
        BigDecimal cacheRead = cacheReadRate.multiply(BigDecimal.valueOf(cacheReadTokens));
        BigDecimal cacheWrite = cacheWriteRate.multiply(BigDecimal.valueOf(cacheWriteTokens));
        return input.add(output).add(cacheRead).add(cacheWrite)
            .divide(MILLION, 8, RoundingMode.HALF_UP);
    }

    private boolean ratesAreConfigured() {
        return this.costPolicy.getInputPerMillion() != null
            && this.costPolicy.getOutputPerMillion() != null;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        BigDecimal checked = Objects.requireNonNull(value, "成本单价不能为空");
        if (checked.signum() < 0) {
            throw new IllegalArgumentException("成本单价不能为负数");
        }
        return checked;
    }

    private void requireText(String value) {
        if (value == null || value.isBlank() || value.length() > 12000) {
            throw new IllegalArgumentException("文本必须非空且不超过 12000 个字符");
        }
    }

    private int tokenCount(Integer value) {
        return value == null ? 0 : value;
    }

    private int totalTokens(Usage usage) {
        return usage == null ? 0 : tokenCount(usage.getTotalTokens());
    }

    private int tokenCount(Usage usage) {
        return usage == null ? 0 : tokenCount(usage.getTotalTokens());
    }

    public record Estimate(int inputTokens, int maxOutputTokens, BigDecimal estimatedCost,
            boolean ratesAreConfigured) {
    }

    public record ChatResult(String content, Integer promptTokens, Integer completionTokens,
            Integer totalTokens, BigDecimal actualCost, boolean nativeUsagePresent,
            Long cacheReadInputTokens, Long cacheWriteInputTokens, ResponseMetadataSupport.Snapshot metadata) {
    }

    public record UsageReport(int firstPromptTokens, int secondPromptTokens,
            int cumulativePromptTokens, int cumulativeCompletionTokens,
            int cumulativeTotalTokens, boolean cumulativeNativeUsagePresent, boolean usageComplete) {
    }

    public record BatchPlan(int maxInputTokens, double reservePercentage, List<Batch> batches) {
    }

    public record Batch(int documentCount, List<Integer> documentTokenCounts, int totalTokens) {
    }

    public static final class BudgetExceededException extends RuntimeException {

        public BudgetExceededException(String message) {
            super(message);
        }

    }

    public static final class ModelOperationException extends RuntimeException {

        public ModelOperationException(String message) {
            super(message);
        }

        public ModelOperationException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
