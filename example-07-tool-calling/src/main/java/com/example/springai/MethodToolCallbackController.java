package com.example.springai;

import com.example.springai.method.MethodToolCallbackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MethodToolCallback 示例控制器
 *
 * 演示如何使用 MethodToolCallback 程序化地创建工具
 */
@RestController
@RequestMapping("/api/method-tool")
public class MethodToolCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MethodToolCallbackController.class);

    private final ChatClient chatClient;
    private final MethodToolCallbackFactory toolFactory;

    public MethodToolCallbackController(
            ChatClient.Builder chatClientBuilder,
            MethodToolCallbackFactory toolFactory) {
        this.chatClient = chatClientBuilder.clone().build();
        this.toolFactory = toolFactory;
    }

    /**
     * 示例 1: 使用 MethodToolCallback 创建的时间工具
     *
     * 演示如何将普通方法转换为工具
     */
    @GetMapping("/time")
    public String timeQuestion(@RequestParam String question) {
        log.info("收到时间相关问题: {}", question);

        // 从工厂获取时间工具
        List<ToolCallback> timeTools = toolFactory.createTimeTools();

        return chatClient
                .prompt(question)
                .tools(timeTools)
                .call()
                .content();
    }

    /**
     * 示例 2: 动态创建工具
     *
     * 演示如何根据配置动态创建工具
     */
    @GetMapping("/dynamic")
    public String dynamicTool(@RequestParam String question) {
        log.info("收到动态工具问题: {}", question);

        // 动态创建工具
        ToolCallback getCurrentTime = toolFactory.createToolFromConfig(
                "com.example.springai.method.TimeTools",
                "getCurrentTime",
                "dynamicGetTime",
                "动态创建的获取当前时间工具"
        );

        return chatClient
                .prompt(question)
                .tools(getCurrentTime)
                .call()
                .content();
    }

    /**
     * 示例 3: 对比不同的工具创建方式
     *
     * 演示 MethodToolCallback 与 @Tool 的对比
     */
    @GetMapping("/compare")
    public String compare(@RequestParam String question) {
        log.info("对比不同工具创建方式: {}", question);

        // 使用 MethodToolCallback 创建的工具
        List<ToolCallback> methodTools = toolFactory.createTimeTools();

        // 可以混合使用不同方式创建的工具
        return chatClient
                .prompt(question)
                .tools(methodTools)
                .call()
                .content();
    }

    /**
     * 示例 4: 静态方法工具
     *
     * 演示如何使用静态方法作为工具
     */
    @GetMapping("/static-method")
    public String staticMethod(@RequestParam String question) {
        log.info("使用静态方法工具: {}", question);

        // 静态方法的工具（daysBetween）
        List<ToolCallback> tools = toolFactory.createTimeTools();

        return chatClient
                .prompt(question)
                .tools(tools)
                .call()
                .content();
    }
}
