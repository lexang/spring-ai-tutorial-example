package com.example.springai.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.example.springai.model.AnalyticsModels.AnalysisPlan;
import com.example.springai.model.AnalyticsModels.Dimension;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsSqlBuilder {

    public SqlQuery build(AnalysisPlan plan, LocalDate startDate, LocalDate endDate, int limit) {
        Objects.requireNonNull(plan, "分析计划不能为空");
        Objects.requireNonNull(startDate, "开始日期不能为空");
        Objects.requireNonNull(endDate, "结束日期不能为空");
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit 必须在 1 到 50 之间");
        }

        String dimensionColumn = switch (plan.dimension()) {
            case REGION -> "region";
            case PRODUCT -> "product";
            case CHANNEL -> "channel";
            case DAY -> "sale_date";
        };
        String aggregateExpression = switch (plan.metric()) {
            case REVENUE -> "SUM(revenue)";
            case ORDERS -> "COUNT(*)";
            case UNITS -> "SUM(units)";
        };
        String orderBy = plan.dimension() == Dimension.DAY ? "label ASC" : "metric_value DESC";
        String sql = """
            SELECT %s AS label, %s AS metric_value
            FROM sales_event
            WHERE sale_date BETWEEN :startDate AND :endDate
            GROUP BY %s
            ORDER BY %s
            LIMIT :limit
            """.formatted(dimensionColumn, aggregateExpression, dimensionColumn, orderBy).strip();

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("startDate", startDate);
        parameters.put("endDate", endDate);
        parameters.put("limit", limit);
        return new SqlQuery(sql, Collections.unmodifiableMap(parameters));
    }

    public record SqlQuery(String sql, Map<String, Object> parameters) {
    }

}
