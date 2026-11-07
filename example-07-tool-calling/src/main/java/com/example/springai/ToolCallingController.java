package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool Calling 示例控制器
 *
 * 演示如何在不同场景下使用工具
 */
@RestController
@RequestMapping("/api")
public class ToolCallingController {

    private static final Logger log = LoggerFactory.getLogger(ToolCallingController.class);

    private final ChatClient chatClient;
    private final DateTimeTools dateTimeTools;
    private final CalculatorTools calculatorTools;
    private final WeatherTools weatherTools;
    private final DatabaseTools databaseTools;

    public ToolCallingController(
            ChatClient.Builder chatClientBuilder,
            DateTimeTools dateTimeTools,
            CalculatorTools calculatorTools,
            WeatherTools weatherTools,
            DatabaseTools databaseTools) {

        this.chatClient = chatClientBuilder.clone().build();
        this.dateTimeTools = dateTimeTools;
        this.calculatorTools = calculatorTools;
        this.weatherTools = weatherTools;
        this.databaseTools = databaseTools;
    }

    /**
     * 日期时间工具示例
     */
    @GetMapping("/datetime")
    public String dateTime(@RequestParam String question) {
        log.info("收到日期时间问题: {}", question);

        String response = chatClient
                .prompt(question)
                .tools(dateTimeTools)  // 注册日期时间工具
                .call()
                .content();

        log.info("返回响应: {}", response);
        return response;
    }

    /**
     * 计算器工具示例
     */
    @GetMapping("/calculator")
    public String calculator(@RequestParam String expression) {
        log.info("收到计算表达式: {}", expression);

        String prompt = "请计算: " + expression;

        String response = chatClient
                .prompt(prompt)
                .tools(calculatorTools)  // 注册计算器工具
                .call()
                .content();

        log.info("返回响应: {}", response);
        return response;
    }

    /**
     * 天气工具示例
     */
    @GetMapping("/weather")
    public String weather(@RequestParam String question) {
        log.info("收到天气问题: {}", question);

        String response = chatClient
                .prompt(question)
                .tools(weatherTools)  // 注册天气工具
                .call()
                .content();

        log.info("返回响应: {}", response);
        return response;
    }

    /**
     * 多工具协同示例
     */
    @GetMapping("/multi-tool")
    public String multiTool(@RequestParam String question) {
        log.info("收到多工具问题: {}", question);

        String response = chatClient
                .prompt(question)
                // 同时注册多个工具
                .tools(dateTimeTools, calculatorTools, weatherTools)
                .call()
                .content();

        log.info("返回响应: {}", response);
        return response;
    }

    /**
     * 数据库工具示例 (Part1 案例3)
     */
    @GetMapping("/database")
    public String database(@RequestParam String question) {
        log.info("收到数据库问题: {}", question);

        String response = chatClient
                .prompt(question)
                .tools(databaseTools)  // 注册数据库工具
                .call()
                .content();

        log.info("返回响应: {}", response);
        return response;
    }

    /**
     * 对比示例: 不使用工具
     */
    @GetMapping("/without-tool")
    public String withoutTool(@RequestParam String question) {
        log.info("收到问题(不使用工具): {}", question);

        String response = chatClient
                .prompt(question)
                // 不注册任何工具
                .call()
                .content();

        log.info("返回响应: {}", response);
        return response;
    }
}
