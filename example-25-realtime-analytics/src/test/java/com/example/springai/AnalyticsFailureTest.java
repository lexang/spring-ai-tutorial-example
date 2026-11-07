package com.example.springai;

import java.time.LocalDate;
import java.util.List;

import com.example.springai.data.AnalyticsRepository;
import com.example.springai.model.AnalyticsModels.AnalysisRequest;
import com.example.springai.service.AnalyticsService;
import com.example.springai.service.AnalyticsSqlBuilder;
import com.example.springai.support.AnalyticsException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalyticsFailureTest {

    @Test
    void modelFailureIsWrappedWithoutQueryingDatabase() {
        ChatModel failingModel = prompt -> {
            throw new IllegalStateException("供应商内部错误");
        };
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(ChatClient.builder(failingModel).build(),
                repository, new AnalyticsSqlBuilder());

        assertThatThrownBy(() -> service.analyze(new AnalysisRequest(
                "比较各地区销售额", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 10)))
            .isInstanceOf(AnalyticsException.class)
            .hasMessage("分析计划生成失败");
        verifyNoInteractions(repository);
    }

    @Test
    void schemaValidButSemanticallyInvalidPlanIsRejected() {
        ChatModel invalidPlanModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"metric":"REVENUE","dimension":"DAY","chartType":"BAR","title":"错误图表"}
                """))));
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(ChatClient.builder(invalidPlanModel).build(),
                repository, new AnalyticsSqlBuilder());

        assertThatThrownBy(() -> service.analyze(new AnalysisRequest(
                "查看每日销售额趋势", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 10)))
            .isInstanceOf(AnalyticsException.class)
            .hasMessageContaining("图表类型与维度不匹配");
        verifyNoInteractions(repository);
    }

}
