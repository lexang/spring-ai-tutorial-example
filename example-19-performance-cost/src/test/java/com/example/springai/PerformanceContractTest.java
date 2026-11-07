package com.example.springai;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.springai.service.PerformanceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PerformanceContractTest {

    @Autowired
    private PerformanceService performanceService;

    @Test
    void estimateUsesInputAndOutputBudget() {
        PerformanceService.Estimate estimate = this.performanceService.estimate(
            "记录输入 token 可以控制成本。", 40);

        assertThat(estimate.inputTokens()).isPositive();
        assertThat(estimate.maxOutputTokens()).isEqualTo(40);
        assertThat(estimate.estimatedCost()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void budgetRejectsBeforeModelCall() {
        assertThatThrownBy(() -> this.performanceService.chat(
            "预算太低的请求", 100, BigDecimal.ZERO))
            .isInstanceOf(PerformanceService.BudgetExceededException.class)
            .hasMessageContaining("超过预算");
    }

    @Test
    void cumulativeUsageDoesNotKeepProviderNativeObject() {
        PerformanceService.UsageReport report = this.performanceService.cumulativeUsage();

        assertThat(report.cumulativeTotalTokens()).isEqualTo(
            report.cumulativePromptTokens() + report.cumulativeCompletionTokens());
        assertThat(report.cumulativeNativeUsagePresent()).isFalse();
    }

    @Test
    void batchingSplitsDocumentsByTokenLimit() {
        PerformanceService.BatchPlan plan = this.performanceService.batch(
            List.of("短文本", "第二个短文本", "第三个短文本"), 12, 0.1);

        assertThat(plan.batches()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(plan.batches()).allSatisfy(batch ->
            assertThat(batch.totalTokens()).isLessThanOrEqualTo(11));
    }

}
