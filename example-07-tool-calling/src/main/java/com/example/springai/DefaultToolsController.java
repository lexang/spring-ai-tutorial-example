package com.example.springai;

import com.example.springai.config.ToolConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Default Tools 示例控制器
 *
 * 演示如何配置和使用默认工具
 */
@RestController
@RequestMapping("/api/default-tools")
public class DefaultToolsController {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolsController.class);

    private final ChatClient chatClientWithDefaults;
    private final ChatClient chatClientNoDefaults;
    private final ToolCallback currentWeather;
    private final ToolCallback weatherForecast;

    public DefaultToolsController(
            ChatClient.Builder chatClientBuilder,
            @Qualifier(ToolConfiguration.CURRENT_WEATHER) ToolCallback currentWeather,
            @Qualifier(ToolConfiguration.WEATHER_FORECAST) ToolCallback weatherForecast) {
        this.currentWeather = currentWeather;
        this.weatherForecast = weatherForecast;

        // 方式 1: 配置默认工具的 ChatClient
        this.chatClientWithDefaults = chatClientBuilder.clone()
                .defaultTools(new DateTimeTools())
                .build();

        // 方式 2: 不配置默认工具的 ChatClient（用于对比）
        this.chatClientNoDefaults = chatClientBuilder.clone().build();

        log.info("DefaultToolsController 初始化完成");
        log.info("chatClientWithDefaults 已配置默认工具: DateTimeTools");
    }

    /**
     * 示例 1: 使用默认工具
     *
     * 不需要手动添加工具，默认工具自动可用
     */
    @GetMapping("/with-defaults")
    public String withDefaults(@RequestParam String question) {
        log.info("使用默认工具: {}", question);

        // 不需要手动添加工具，默认工具自动可用
        return chatClientWithDefaults
                .prompt(question)
                .call()
                .content();
    }

    /**
     * 示例 2: 不使用默认工具（对比）
     *
     * 如果不配置默认工具，AI 无法回答需要实时数据的问题
     */
    @GetMapping("/without-defaults")
    public String withoutDefaults(@RequestParam String question) {
        log.info("不使用默认工具: {}", question);

        // 没有默认工具，AI 无法获取实时信息
        return chatClientNoDefaults
                .prompt(question)
                .call()
                .content();
    }

    /**
     * 示例 3: 运行时工具追加到默认工具
     *
     * ChatClient 会合并默认工具与本次调用提供的工具。
     */
    @GetMapping("/runtime-append")
    public String runtimeAppend(@RequestParam String question) {
        log.info("运行时工具追加到默认工具: {}", question);

        WeatherTools weatherTools = new WeatherTools();

        // DateTimeTools 仍然可用，WeatherTools 只对本次调用生效
        return chatClientWithDefaults
                .prompt(question)
                .tools(weatherTools)
                .call()
                .content();
    }

    /**
     * 示例 4: 同时使用默认工具和运行时工具
     *
     * 默认工具会自动保留，只需要添加本次调用所需的工具。
     */
    @GetMapping("/combined")
    public String combined(@RequestParam String question) {
        log.info("同时使用默认工具和运行时工具: {}", question);

        WeatherTools weatherTools = new WeatherTools();

        // 默认的 DateTimeTools 与本次调用的 WeatherTools 都可用
        return chatClientWithDefaults
                .prompt(question)
                .tools(weatherTools)
                .call()
                .content();
    }

    /**
     * 示例 5: 使用显式注入的 ToolCallback Bean
     *
     * Spring AI 2.0 不再按 Bean 名称解析工具，必须传入 ToolCallback。
     */
    @GetMapping("/bean-tools")
    public String beanTools(@RequestParam String question) {
        log.info("使用显式注入的 ToolCallback Bean: {}", question);

        return chatClientWithDefaults
                .prompt(question)
                .tools(currentWeather, weatherForecast)
                .call()
                .content();
    }

    /** 示例 6: 多个运行时工具同样会追加到默认工具。 */
    @GetMapping("/merged")
    public String merged(@RequestParam String question) {
        log.info("使用合并辅助方法: {}", question);

        WeatherTools weatherTools = new WeatherTools();

        return chatClientWithDefaults
                .prompt(question)
                .tools(weatherTools, currentWeather)
                .call()
                .content();
    }
}
