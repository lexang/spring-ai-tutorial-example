package com.example.springai.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsModels {

    private AnalyticsModels() {
    }

    public enum Metric {
        REVENUE("销售额", "元"),
        ORDERS("订单数", "笔"),
        UNITS("销量", "件");

        private final String label;

        private final String unit;

        Metric(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }

        public String label() {
            return this.label;
        }

        public String unit() {
            return this.unit;
        }
    }

    public enum Dimension {
        REGION("地区"),
        PRODUCT("产品"),
        CHANNEL("渠道"),
        DAY("日期");

        private final String label;

        Dimension(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public enum ChartType {
        BAR,
        LINE
    }

    public record AnalysisPlan(Metric metric, Dimension dimension, ChartType chartType, String title) {
    }

    public record AnalysisRequest(String question, LocalDate startDate, LocalDate endDate, Integer limit) {
    }

    public record DataPoint(String label, BigDecimal value) {
    }

    public record ChartSpec(String type, String title, String xAxisLabel, String yAxisLabel,
            List<String> labels, List<BigDecimal> values) {
    }

    public record AnalysisResult(String question, AnalysisPlan plan, String sql,
            Map<String, Object> parameters, List<DataPoint> data, ChartSpec chart,
            String summary, List<String> warnings, Instant generatedAt) {
    }

    public record EventRequest(LocalDate saleDate, String region, String product, String channel,
            Integer units, BigDecimal revenue) {
    }

    public record EventReceipt(int insertedRows, long totalEvents, Instant acceptedAt) {
    }

    public record Option(String code, String label) {
    }

    public record Catalog(List<Option> metrics, List<Option> dimensions, List<String> regions,
            List<String> products, List<String> channels) {
    }

}
