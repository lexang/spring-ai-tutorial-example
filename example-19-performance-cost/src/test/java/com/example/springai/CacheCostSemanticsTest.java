package com.example.springai;

import java.math.BigDecimal;
import java.util.List;

import com.example.springai.config.CostPolicyProperties;
import com.example.springai.service.PerformanceService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;

class CacheCostSemanticsTest {

    @Test
    void includedCacheReplacesRegularInputTokens() {
        assertThat(service(true).chat("测试缓存成本", 20, null).actualCost())
                .isEqualByComparingTo("0.000087");
    }

    @Test
    void excludedCacheDoesNotSubtractFromUncachedInput() {
        assertThat(service(false).chat("测试缓存成本", 20, null).actualCost())
                .isEqualByComparingTo("0.000117");
    }

    private PerformanceService service(boolean includesCache) {
        CostPolicyProperties policy = new CostPolicyProperties();
        policy.setInputPerMillion(BigDecimal.ONE);
        policy.setOutputPerMillion(BigDecimal.valueOf(2));
        policy.setCacheReadPerMillion(new BigDecimal("0.1"));
        policy.setCacheWritePerMillion(new BigDecimal("0.5"));
        policy.setPromptTokensIncludeCache(includesCache);
        ChatModel model = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage("测试回答"))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(100, 5, 105, null, 20L, 10L))
                        .build());
        return new PerformanceService(ChatClient.create(model), policy);
    }
}
