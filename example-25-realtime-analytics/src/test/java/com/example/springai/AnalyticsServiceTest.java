package com.example.springai;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.springai.model.AnalyticsModels.AnalysisRequest;
import com.example.springai.model.AnalyticsModels.AnalysisResult;
import com.example.springai.model.AnalyticsModels.ChartType;
import com.example.springai.model.AnalyticsModels.Dimension;
import com.example.springai.model.AnalyticsModels.EventRequest;
import com.example.springai.model.AnalyticsModels.Metric;
import com.example.springai.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AnalyticsServiceTest {

    @Autowired
    private AnalyticsService service;

    @Test
    void analyzesCurrentRevenueByRegion() {
        AnalysisResult result = this.service.analyze(new AnalysisRequest(
                "比较各地区销售额", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 10));

        assertThat(result.plan().metric()).isEqualTo(Metric.REVENUE);
        assertThat(result.plan().dimension()).isEqualTo(Dimension.REGION);
        assertThat(result.plan().chartType()).isEqualTo(ChartType.BAR);
        assertThat(result.data()).hasSize(4);
        assertThat(result.data().get(0).label()).isEqualTo("华南");
        assertThat(result.data().get(0).value()).isEqualByComparingTo("4588.00");
        assertThat(result.sql()).doesNotContain(result.question());
    }

    @Test
    void createsDailyOrderTimeline() {
        AnalysisResult result = this.service.analyze(new AnalysisRequest(
                "查看每日订单趋势", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 20));

        assertThat(result.plan().metric()).isEqualTo(Metric.ORDERS);
        assertThat(result.plan().dimension()).isEqualTo(Dimension.DAY);
        assertThat(result.chart().type()).isEqualTo("line");
        assertThat(result.chart().labels()).containsExactly(
                "2026-07-20", "2026-07-21", "2026-07-22", "2026-07-23",
                "2026-07-24", "2026-07-25", "2026-07-26");
    }

    @Test
    void newlyInsertedEventAppearsInNextAnalysis() {
        LocalDate date = LocalDate.of(2030, 3, 1);
        AnalysisRequest request = new AnalysisRequest("按渠道查看销售额", date, date, 10);
        assertThat(this.service.analyze(request).data()).isEmpty();

        this.service.addEvent(new EventRequest(date, "华东", "Spring AI 课程", "官网", 2,
                new BigDecimal("598.00")));

        AnalysisResult result = this.service.analyze(request);
        assertThat(result.data()).singleElement()
            .satisfies(point -> {
                assertThat(point.label()).isEqualTo("官网");
                assertThat(point.value()).isEqualByComparingTo("598.00");
            });
    }

    @Test
    void rejectsInvalidDateRange() {
        assertThatThrownBy(() -> this.service.analyze(new AnalysisRequest(
                "比较销售额", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 20), 10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能晚于");
    }

    @Test
    void rejectsUnknownEventCatalogValue() {
        assertThatThrownBy(() -> this.service.addEvent(new EventRequest(
                LocalDate.of(2026, 7, 26), "未知地区", "Spring AI 课程", "官网", 1,
                new BigDecimal("299.00"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("允许的目录");
    }

}
