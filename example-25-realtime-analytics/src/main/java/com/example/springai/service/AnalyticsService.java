package com.example.springai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.example.springai.data.AnalyticsRepository;
import com.example.springai.model.AnalyticsModels.AnalysisPlan;
import com.example.springai.model.AnalyticsModels.AnalysisRequest;
import com.example.springai.model.AnalyticsModels.AnalysisResult;
import com.example.springai.model.AnalyticsModels.Catalog;
import com.example.springai.model.AnalyticsModels.ChartSpec;
import com.example.springai.model.AnalyticsModels.ChartType;
import com.example.springai.model.AnalyticsModels.DataPoint;
import com.example.springai.model.AnalyticsModels.Dimension;
import com.example.springai.model.AnalyticsModels.EventReceipt;
import com.example.springai.model.AnalyticsModels.EventRequest;
import com.example.springai.model.AnalyticsModels.Metric;
import com.example.springai.model.AnalyticsModels.Option;
import com.example.springai.service.AnalyticsSqlBuilder.SqlQuery;
import com.example.springai.support.AnalyticsException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private static final int DEFAULT_LIMIT = 10;

    private static final int MAX_QUESTION_LENGTH = 500;

    private static final List<String> REGIONS = List.of("华东", "华南", "华北", "西南");

    private static final List<String> PRODUCTS = List.of("Spring AI 课程", "向量数据库课程", "MCP 工作坊");

    private static final List<String> CHANNELS = List.of("官网", "企业直销", "渠道合作");

    private final ChatClient chatClient;

    private final AnalyticsRepository repository;

    private final AnalyticsSqlBuilder sqlBuilder;

    public AnalyticsService(ChatClient chatClient, AnalyticsRepository repository,
            AnalyticsSqlBuilder sqlBuilder) {
        this.chatClient = chatClient;
        this.repository = repository;
        this.sqlBuilder = sqlBuilder;
    }

    public AnalysisResult analyze(AnalysisRequest request) {
        ValidatedAnalysis validated = validateAnalysis(request);
        AnalysisPlan plan;
        try {
            plan = this.chatClient.prompt()
                .user(user -> user.text("""
                        根据用户问题选择一个指标、一个分组维度和一种图表类型。
                        指标只能是 REVENUE、ORDERS、UNITS；维度只能是 REGION、PRODUCT、CHANNEL、DAY；
                        DAY 必须使用 LINE，其他维度必须使用 BAR。标题使用简短中文，不得包含 SQL。
                        用户问题开始
                        {question}
                        用户问题结束
                        """).param("question", validated.question()))
                .call()
                .entity(AnalysisPlan.class, spec -> spec.validateSchema());
        }
        catch (RuntimeException exception) {
            throw new AnalyticsException("分析计划生成失败", exception);
        }
        plan = validatePlan(plan);

        SqlQuery query = this.sqlBuilder.build(plan, validated.startDate(), validated.endDate(), validated.limit());
        List<DataPoint> data = this.repository.query(query);
        ChartSpec chart = toChart(plan, data);
        return new AnalysisResult(validated.question(), plan, query.sql(), query.parameters(), data, chart,
                summarize(plan, data),
                List.of("模型只选择受控枚举，应用不会执行模型自由文本 SQL",
                        "H2 与内置样例数据仅用于教学，生产环境应使用只读账号和独立分析库"),
                Instant.now());
    }

    public EventReceipt addEvent(EventRequest request) {
        EventRequest validated = validateEvent(request);
        int inserted = this.repository.insert(validated);
        return new EventReceipt(inserted, this.repository.count(), Instant.now());
    }

    public Catalog catalog() {
        List<Option> metrics = Arrays.stream(Metric.values())
            .map(metric -> new Option(metric.name(), metric.label()))
            .toList();
        List<Option> dimensions = Arrays.stream(Dimension.values())
            .map(dimension -> new Option(dimension.name(), dimension.label()))
            .toList();
        return new Catalog(metrics, dimensions, REGIONS, PRODUCTS, CHANNELS);
    }

    private ValidatedAnalysis validateAnalysis(AnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String question = required(request.question(), "question", MAX_QUESTION_LENGTH);
        LocalDate startDate = requireDate(request.startDate(), "startDate");
        LocalDate endDate = requireDate(request.endDate(), "endDate");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate 不能晚于 endDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 366) {
            throw new IllegalArgumentException("查询时间范围不能超过 366 天");
        }
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit 必须在 1 到 50 之间");
        }
        return new ValidatedAnalysis(question, startDate, endDate, limit);
    }

    private AnalysisPlan validatePlan(AnalysisPlan plan) {
        if (plan == null || plan.metric() == null || plan.dimension() == null || plan.chartType() == null) {
            throw new AnalyticsException("分析模型没有返回完整计划");
        }
        ChartType expected = plan.dimension() == Dimension.DAY ? ChartType.LINE : ChartType.BAR;
        if (plan.chartType() != expected) {
            throw new AnalyticsException("分析模型返回的图表类型与维度不匹配");
        }
        if (plan.title() == null || plan.title().isBlank() || plan.title().trim().length() > 100) {
            throw new AnalyticsException("分析模型没有返回有效的图表标题");
        }
        String title = plan.title().trim();
        if (title.contains("\n") || title.contains("\r") || title.toLowerCase(Locale.ROOT).contains("sql")) {
            throw new AnalyticsException("分析模型返回了不安全的图表标题");
        }
        return new AnalysisPlan(plan.metric(), plan.dimension(), plan.chartType(), title);
    }

    private EventRequest validateEvent(EventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        LocalDate saleDate = requireDate(request.saleDate(), "saleDate");
        if (saleDate.isBefore(LocalDate.of(2000, 1, 1)) || saleDate.isAfter(LocalDate.of(2100, 12, 31))) {
            throw new IllegalArgumentException("saleDate 必须在 2000-01-01 到 2100-12-31 之间");
        }
        String region = allowed(request.region(), "region", REGIONS);
        String product = allowed(request.product(), "product", PRODUCTS);
        String channel = allowed(request.channel(), "channel", CHANNELS);
        if (request.units() == null || request.units() < 1 || request.units() > 10_000) {
            throw new IllegalArgumentException("units 必须在 1 到 10000 之间");
        }
        if (request.revenue() == null || request.revenue().signum() < 0
                || request.revenue().compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("revenue 必须在 0 到 1000000 之间");
        }
        if (request.revenue().scale() > 2) {
            throw new IllegalArgumentException("revenue 最多保留两位小数");
        }
        return new EventRequest(saleDate, region, product, channel, request.units(),
                request.revenue().setScale(2, RoundingMode.UNNECESSARY));
    }

    private ChartSpec toChart(AnalysisPlan plan, List<DataPoint> data) {
        return new ChartSpec(plan.chartType().name().toLowerCase(), plan.title(), plan.dimension().label(),
                plan.metric().label() + "（" + plan.metric().unit() + "）",
                data.stream().map(DataPoint::label).toList(),
                data.stream().map(DataPoint::value).toList());
    }

    private String summarize(AnalysisPlan plan, List<DataPoint> data) {
        if (data.isEmpty()) {
            return "指定时间范围内没有可分析的数据。";
        }
        DataPoint peak = data.stream().max(Comparator.comparing(DataPoint::value)).orElseThrow();
        return "%s：最高项为%s，%s为%s%s，共返回%d个分组。".formatted(
                plan.title(), peak.label(), plan.metric().label(), peak.value().stripTrailingZeros().toPlainString(),
                plan.metric().unit(), data.size());
    }

    private String allowed(String value, String field, List<String> allowedValues) {
        String normalized = required(value, field, 80);
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException(field + " 不在允许的目录中");
        }
        return normalized;
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private LocalDate requireDate(LocalDate value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private record ValidatedAnalysis(String question, LocalDate startDate, LocalDate endDate, int limit) {
    }

}
