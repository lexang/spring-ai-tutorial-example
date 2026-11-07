package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能路由服务
 * <p>
 * 根据问题的特征自动选择最合适的模型
 *
 * @author Spring AI Tutorial
 */
@Service
public class SmartRoutingService {

    private static final Logger log = LoggerFactory.getLogger(SmartRoutingService.class);

    private final ChatClient fastClient;
    private final ChatClient standardClient;
    private final ChatClient creativeClient;
    private final ChatClient analyticalClient;

    public SmartRoutingService(
            @Qualifier("fast") ChatClient fastClient,
            @Qualifier("standard") ChatClient standardClient,
            @Qualifier("creative") ChatClient creativeClient,
            @Qualifier("analytical") ChatClient analyticalClient) {

        this.fastClient = fastClient;
        this.standardClient = standardClient;
        this.creativeClient = creativeClient;
        this.analyticalClient = analyticalClient;
    }

    /**
     * 智能处理问题 - 自动选择最合适的模型
     *
     * @param question 用户问题
     * @return RoutingResult 包含答案和使用的模型
     */
    public RoutingResult processQuestion(String question) {
        // 分析问题类型
        QuestionType type = analyzeQuestionType(question);

        // 根据类型选择模型
        ChatClient selectedClient;
        String modelName;

        switch (type) {
            case SIMPLE -> {
                selectedClient = fastClient;
                modelName = "fast";
                log.info("检测到简单问题，使用快速模型");
            }
            case CREATIVE -> {
                selectedClient = creativeClient;
                modelName = "creative";
                log.info("检测到创意请求，使用创意模型");
            }
            case ANALYTICAL -> {
                selectedClient = analyticalClient;
                modelName = "analytical";
                log.info("检测到分析请求，使用分析模型");
            }
            default -> {
                selectedClient = standardClient;
                modelName = "standard";
                log.info("使用标准模型");
            }
        }

        // 调用选定的模型
        String response = selectedClient.prompt()
                .user(question)
                .call()
                .content();

        return new RoutingResult(response, modelName, type.name());
    }

    /**
     * 分析问题类型
     */
    private QuestionType analyzeQuestionType(String question) {
        String lowerQuestion = question.toLowerCase();

        // 创意类关键词
        if (containsAny(lowerQuestion, "写", "创作", "诗", "故事", "头脑风暴", "想象")) {
            return QuestionType.CREATIVE;
        }

        // 分析类关键词
        if (containsAny(lowerQuestion, "分析", "对比", "评估", "为什么", "原因", "如何")) {
            return QuestionType.ANALYTICAL;
        }

        // 简单问题特征
        if (question.length() < 20 && containsAny(lowerQuestion, "是什么", "什么是", "谁", "哪里")) {
            return QuestionType.SIMPLE;
        }

        // 默认使用标准模型
        return QuestionType.STANDARD;
    }

    /**
     * 检查字符串是否包含任意关键词
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 问题类型枚举
     */
    private enum QuestionType {
        SIMPLE,      // 简单问题
        STANDARD,    // 标准问题
        CREATIVE,    // 创意问题
        ANALYTICAL   // 分析问题
    }
}

/**
 * 路由结果
 */
record RoutingResult(String response, String usedModel, String detectedType) {}

/**
 * 智能路由控制器
 */
@RestController
@RequestMapping("/api/smart")
class SmartRoutingController {

    private final SmartRoutingService routingService;

    public SmartRoutingController(SmartRoutingService routingService) {
        this.routingService = routingService;
    }

    /**
     * 智能路由接口
     * <p>
     * GET /api/smart?question=你的问题
     * <p>
     * 系统会自动分析问题类型并选择最合适的模型
     */
    @GetMapping
    public RoutingResult smartRoute(@RequestParam String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        return routingService.processQuestion(question);
    }
}
