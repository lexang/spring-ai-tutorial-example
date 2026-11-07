package com.example.springai.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 异常处理示例工具
 *
 * 演示工具中如何处理各种异常情况
 * RuntimeException（运行时异常）
 * 错误消息会被发送回 AI 模型
 * 让模型理解并可能提供解决方案
 *
 *
 * Checked Exceptions（受检异常）和 Error
 * 例如：IOException、OutOfMemoryError
 * 始终向上抛出，不发送给模型
 */
@Component
public class MathTools {

    private static final Logger log = LoggerFactory.getLogger(MathTools.class);

    /**
     * 除法运算 - 抛出异常的示例
     *
     * 当除数为 0 时抛出 IllegalArgumentException
     * 异常会被包装为 ToolExecutionException 并传递给异常处理器
     * 这样模型可以给用户更友好的反馈
     */
    @Tool(description = "计算两个数字的商,除数不能为0")
    public double divide(
            @ToolParam(description = "被除数") double a,
            @ToolParam(description = "除数") double b) {

        log.info("工具被调用: divide, a={}, b={}", a, b);

        if (b == 0) {
            log.error("除数为0,抛出异常");
            throw new IllegalArgumentException("除数不能为0");
        }

        double result = a / b;
        log.info("除法计算成功: {} / {} = {}", a, b, result);
        return result;
    }

    /**
     * 平方根运算 - 友好的错误处理
     *
     * 返回错误消息而不是抛出异常
     * 这样模型可以给用户更友好的反馈
     */
    @Tool(description = "计算数字的平方根,数字必须为非负数")
    public String squareRoot(
            @ToolParam(description = "要计算平方根的数字") double number) {

        log.info("工具被调用: squareRoot, number={}", number);

        if (number < 0) {
            String error = "错误: 无法计算负数的平方根";
            log.warn(error);
            return error;  // 返回错误消息而不是抛出异常
        }

        double result = Math.sqrt(number);
        log.info("平方根计算成功: sqrt({}) = {}", number, result);
        return String.format("%.4f", result);
    }

    /**
     * 阶乘计算 - 参数验证
     */
    @Tool(description = "计算非负整数的阶乘")
    public String factorial(
            @ToolParam(description = "要计算阶乘的整数,必须在 0-20 之间") int n) {

        log.info("工具被调用: factorial, n={}", n);

        // 参数验证
        if (n < 0) {
            return "错误: 负数没有阶乘";
        }

        if (n > 20) {
            return "错误: 数字太大,请输入 0-20 之间的整数";
        }

        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }

        log.info("阶乘计算成功: {}! = {}", n, result);
        return String.format("%d! = %d", n, result);
    }

    /**
     * 模拟数据库错误
     *
     * 用于测试异常处理
     *
     * 这样模型可以给用户更友好的反馈
     */
    @Tool(description = "模拟数据库查询 (可能失败)")
    public String queryDatabase(
            @ToolParam(description = "查询 ID") int id) {

        log.info("工具被调用: queryDatabase, id={}", id);

        // 模拟特定 ID 导致数据库错误
        if (id == 999) {
            log.error("模拟数据库连接失败");
            throw new RuntimeException("数据库连接失败: Connection timeout");
        }

        if (id < 0) {
            return "错误: ID 必须为正整数";
        }

        log.info("数据库查询成功: id={}", id);
        return "查询结果: Record #" + id;
    }

    /**
     * 模拟网络错误
     */
    @Tool(description = "模拟 API 调用 (可能超时)")
    public String callExternalApi(
            @ToolParam(description = "API 端点") String endpoint) {

        log.info("工具被调用: callExternalApi, endpoint={}", endpoint);

        // 模拟网络超时
        if ("timeout".equals(endpoint)) {
            log.error("模拟网络超时");
            throw new RuntimeException("网络请求超时: Read timeout");
        }

        log.info("API 调用成功: endpoint={}", endpoint);
        return "API 响应: Success from " + endpoint;
    }

    /**
     * 由应用程序处理错误
     * // 配置：spring.ai.tools.throw-exception-on-error=true   错误作为异常抛出给调用者处理
     *
     * try {
     *     String response = ChatClient.create(chatModel)
     *         .prompt("查询客户 42 的信息")
     *         .tools(new CustomerTools())
     *         .call()
     *         .content();
     * } catch (ToolExecutionException e) {
     *     // 应用程序捕获并处理异常
     *     logger.error("工具调用失败", e);
     *     return "系统暂时无法处理您的请求，请稍后重试";
     * }
     */
}
