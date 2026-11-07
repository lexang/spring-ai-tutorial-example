package com.example.springai;

import com.example.springai.context.CustomerTools;
import com.example.springai.config.ToolConfiguration;
import com.example.springai.converter.CustomerToolsWithConverter;
import com.example.springai.exception.MathTools;
import com.example.springai.function.TimeService;
import com.example.springai.function.WeatherService;
import com.example.springai.model.WeatherRequest;
import com.example.springai.rag.KnowledgeBaseTools;
import com.example.springai.schema.BookingTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 高级 Tool Calling 示例控制器
 *
 */
@RestController
@RequestMapping("/api/advanced")
public class AdvancedToolCallingController {

    private static final Logger log = LoggerFactory.getLogger(AdvancedToolCallingController.class);

    private final ChatClient chatClient;
    private final CustomerTools customerTools;
    private final KnowledgeBaseTools knowledgeBaseTools;
    private final CustomerToolsWithConverter customerToolsWithConverter;
    private final MathTools mathTools;
    private final FileSystemTools fileSystemTools;
    private final DatabaseTools databaseTools;
    private final BookingTools bookingTools;
    private final ToolCallback currentWeather;
    private final ToolCallback weatherForecast;

    public AdvancedToolCallingController(
            ChatClient.Builder chatClientBuilder,
            CustomerTools customerTools,
            KnowledgeBaseTools knowledgeBaseTools,
            CustomerToolsWithConverter customerToolsWithConverter,
            MathTools mathTools,
            DatabaseTools databaseTools,
            BookingTools bookingTools,
            @Qualifier(ToolConfiguration.CURRENT_WEATHER) ToolCallback currentWeather,
            @Qualifier(ToolConfiguration.WEATHER_FORECAST) ToolCallback weatherForecast) {

        this.chatClient = chatClientBuilder.clone().build();
        this.customerTools = customerTools;
        this.knowledgeBaseTools = knowledgeBaseTools;
        this.customerToolsWithConverter = customerToolsWithConverter;
        this.mathTools = mathTools;
        this.databaseTools = databaseTools;
        this.bookingTools = bookingTools;
        this.currentWeather = currentWeather;
        this.weatherForecast = weatherForecast;

        // 初始化文件系统工具,使用当前目录作为基础目录
        String baseDir = Paths.get("").toAbsolutePath().toString();
        this.fileSystemTools = new FileSystemTools(baseDir);

        log.info("AdvancedToolCallingController 初始化完成");
    }

    /**
     * Part1: 文件系统工具示例
     */
    @GetMapping("/filesystem")
    public String filesystem(@RequestParam String question) {
        log.info("收到文件系统问题: {}", question);

        return chatClient
                .prompt(question)
                .tools(fileSystemTools)
                .call()
                .content();
    }


    /**
     * Part2: Function 接口示例
     *
     * 使用 FunctionToolCallback 构建工具
     */
    @GetMapping("/function-tool")
    public String functionTool(@RequestParam String question) {
        log.info("收到 Function 工具问题: {}", question);

        // 使用 FunctionToolCallback.builder 创建工具
        ToolCallback weatherTool = FunctionToolCallback.builder(
                        "currentWeather",
                        new WeatherService())
                .description("获取指定位置的当前天气")
                .inputType(WeatherRequest.class)
                .build();

        return chatClient
                .prompt(question)
                .tools(weatherTool)
                .call()
                .content();
    }

    /**
     * Part2: Lambda 表达式 Function 示例
     */
    @GetMapping("/lambda-tool")
    public String lambdaTool(@RequestParam String question) {
        log.info("收到 Lambda 工具问题: {}", question);

        // 使用 Lambda 表达式创建简单工具
        ToolCallback timeTool = FunctionToolCallback.builder(
                        "getTime",
                        TimeService.getCurrentTime())
                .description("获取当前时间")
                .inputType(Void.class)
                .build();

        return chatClient
                .prompt(question)
                .tools(timeTool)
                .call()
                .content();
    }

    /**
     * Part2: Bean 方式注册的工具
     *
     * Spring AI 2.0 显式注入 ToolCallback Bean，并通过 tools() 注册
     */
    @GetMapping("/bean-tool")
    public String beanTool(@RequestParam String question) {
        log.info("收到 Bean 工具问题: {}", question);

        return chatClient
                .prompt(question)
                // Spring AI 2.0 要求显式注入 ToolCallback Bean
                .tools(currentWeather, weatherForecast)
                .call()
                .content();
    }

    /**
     * Part2: ToolContext 示例 - 多租户场景
     *
     * 演示如何传递上下文信息(租户ID、用户ID等)
     */
    @GetMapping("/tool-context")
    public String toolContext(
            @RequestParam String question,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tenant-a") String tenantId,
            @RequestHeader(value = "X-User-ID", defaultValue = "user-001") String userId) {

        log.info("收到 ToolContext 问题: {}, tenantId={}, userId={}", question, tenantId, userId);

        return chatClient
                .prompt(question)
                .tools(customerTools)
                // 传递 ToolContext
                .toolContext(Map.of(
                        "tenantId", tenantId,
                        "userId", userId,
                        "requestId", UUID.randomUUID().toString()
                ))
                .call()
                .content();
    }

    /**
     * Part2: Return Direct
     *
     */
    @GetMapping("/return-direct")
    public String returnDirect(@RequestParam String query) {
        log.info("收到 ReturnDirect 问题: {}", query);

        return chatClient
                .prompt("搜索: " + query)
                .tools(knowledgeBaseTools)
                .call()
                .content();
    }



    /**
     * Part2: 自定义 ResultConverter 示例
     *
     * 使用自定义转换器格式化工具返回结果
     */
    @GetMapping("/custom-converter")
    public String customConverter(@RequestParam String question) {
        log.info("收到自定义转换器问题: {}", question);

        return chatClient
                .prompt(question)
                .tools(customerToolsWithConverter)
                .call()
                .content();
    }

    /**
     * Part2: 异常处理示例
     *
     * 演示工具执行中的异常处理
     */
    @GetMapping("/exception-handling")
    public String exceptionHandling(@RequestParam String expression) {
        log.info("收到可能导致异常的计算: {}", expression);

        try {
            return chatClient
                    .prompt("计算: " + expression)
                    .tools(mathTools)
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("工具执行失败", e);
            return "计算失败，请检查表达式后重试";
        }
    }

    /**
     * Part2: 综合示例 - 多种高级特性组合
     *
     * 同时使用多个高级特性
     */
    @GetMapping("/comprehensive")
    public String comprehensive(
            @RequestParam String question,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tenant-a") String tenantId) {

        log.info("收到综合问题: {}, tenantId={}", question, tenantId);

        // 创建 Function 工具
        ToolCallback timeTool = FunctionToolCallback.builder(
                        "getTime",
                        TimeService.getCurrentTime())
                .description("获取当前时间")
                .inputType(Void.class)
                .build();

        return chatClient
                .prompt(question)
                // 混合使用多种工具
                .tools(customerTools, knowledgeBaseTools, mathTools, timeTool)
                // 传递上下文
                .toolContext(Map.of(
                        "tenantId", tenantId,
                        "userId", "comprehensive-user"
                ))
                .call()
                .content();
    }

    /**
     * Part2: JSON Schema 注解示例 (@Schema)
     *
     * 演示如何使用 Swagger 的 @Schema 注解提供详细的参数信息
     * BookingRequest 使用了 @Schema 注解，包含验证规则、示例值等
     */
    @GetMapping("/schema-annotation")
    public String schemaAnnotation(@RequestParam String question) {
        log.info("收到 Schema 注解示例问题: {}", question);

        return chatClient
                .prompt(question)
                .tools(bookingTools)
                .call()
                .content();
    }


}
