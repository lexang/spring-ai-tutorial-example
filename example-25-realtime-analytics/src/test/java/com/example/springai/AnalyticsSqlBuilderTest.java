package com.example.springai;

import java.time.LocalDate;

import com.example.springai.model.AnalyticsModels.AnalysisPlan;
import com.example.springai.model.AnalyticsModels.ChartType;
import com.example.springai.model.AnalyticsModels.Dimension;
import com.example.springai.model.AnalyticsModels.Metric;
import com.example.springai.service.AnalyticsSqlBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsSqlBuilderTest {

    private final AnalyticsSqlBuilder builder = new AnalyticsSqlBuilder();

    @Test
    void buildsAllowlistedParameterizedRevenueQuery() {
        AnalysisPlan plan = new AnalysisPlan(Metric.REVENUE, Dimension.REGION, ChartType.BAR, "地区销售额对比");

        AnalyticsSqlBuilder.SqlQuery query = this.builder.build(plan,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 10);

        assertThat(query.sql()).contains("SELECT region AS label, SUM(revenue) AS metric_value")
            .contains("sale_date BETWEEN :startDate AND :endDate")
            .contains("ORDER BY metric_value DESC")
            .contains("LIMIT :limit");
        assertThat(query.parameters()).containsEntry("limit", 10)
            .containsEntry("startDate", LocalDate.of(2026, 7, 20));
    }

    @Test
    void dayDimensionUsesAscendingTimeline() {
        AnalysisPlan plan = new AnalysisPlan(Metric.ORDERS, Dimension.DAY, ChartType.LINE, "日期订单数趋势");

        AnalyticsSqlBuilder.SqlQuery query = this.builder.build(plan,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 20);

        assertThat(query.sql()).contains("SELECT sale_date AS label, COUNT(*) AS metric_value")
            .contains("GROUP BY sale_date")
            .contains("ORDER BY label ASC");
    }

    @Test
    void rejectsUnboundedLimit() {
        AnalysisPlan plan = new AnalysisPlan(Metric.UNITS, Dimension.PRODUCT, ChartType.BAR, "产品销量对比");

        assertThatThrownBy(() -> this.builder.build(plan,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 51))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1 到 50");
    }

}
