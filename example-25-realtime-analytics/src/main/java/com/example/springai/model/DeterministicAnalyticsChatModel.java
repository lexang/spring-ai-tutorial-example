package com.example.springai.model;

import java.util.List;

import com.example.springai.model.AnalyticsModels.ChartType;
import com.example.springai.model.AnalyticsModels.Dimension;
import com.example.springai.model.AnalyticsModels.Metric;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** 仅用于离线结构化输出测试的确定性分析模型。 */
public final class DeterministicAnalyticsChatModel implements ChatModel {

    private static final String QUESTION_START = "用户问题开始";

    private static final String QUESTION_END = "用户问题结束";

    @Override
    public ChatResponse call(Prompt prompt) {
        String question = extractQuestion(prompt.getUserMessage().getText());
        Metric metric = selectMetric(question);
        Dimension dimension = selectDimension(question);
        ChartType chartType = dimension == Dimension.DAY ? ChartType.LINE : ChartType.BAR;
        String title = dimension.label() + metric.label() + (chartType == ChartType.LINE ? "趋势" : "对比");
        String json = """
            {"metric":"%s","dimension":"%s","chartType":"%s","title":"%s"}
            """.formatted(metric, dimension, chartType, title);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private String extractQuestion(String text) {
        int start = text.indexOf(QUESTION_START);
        int end = text.indexOf(QUESTION_END);
        if (start < 0 || end <= start) {
            return text;
        }
        return text.substring(start + QUESTION_START.length(), end).trim();
    }

    private Metric selectMetric(String question) {
        if (question.contains("订单")) {
            return Metric.ORDERS;
        }
        if (question.contains("销量") || question.contains("件数") || question.contains("数量")) {
            return Metric.UNITS;
        }
        return Metric.REVENUE;
    }

    private Dimension selectDimension(String question) {
        if (question.contains("趋势") || question.contains("每日") || question.contains("每天")
                || question.contains("按天") || question.contains("日期")) {
            return Dimension.DAY;
        }
        if (question.contains("地区") || question.contains("区域")) {
            return Dimension.REGION;
        }
        if (question.contains("渠道")) {
            return Dimension.CHANNEL;
        }
        if (question.contains("产品") || question.contains("课程")) {
            return Dimension.PRODUCT;
        }
        return Dimension.REGION;
    }

}
