package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 计算器工具类
 *
 * 提供基本的算术运算功能
 */
@Component
public class
CalculatorTools {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTools.class);

    /**
     * 加法运算
     */
    @Tool(description = "计算两个数字的和")
    public double add(
            @ToolParam(description = "第一个数字") double a,
            @ToolParam(description = "第二个数字") double b) {

        log.info("工具被调用: add, 参数: a={}, b={}", a, b);
        double result = a + b;
        log.info("计算结果: {}", result);
        return result;
    }

    /**
     * 减法运算
     */
    @Tool(description = "计算两个数字的差")
    public double subtract(
            @ToolParam(description = "被减数") double a,
            @ToolParam(description = "减数") double b) {

        log.info("工具被调用: subtract, 参数: a={}, b={}", a, b);
        double result = a - b;
        log.info("计算结果: {}", result);
        return result;
    }

    /**
     * 乘法运算
     */
    @Tool(description = "计算两个数字的积")
    public double multiply(
            @ToolParam(description = "第一个数字") double a,
            @ToolParam(description = "第二个数字") double b) {

        log.info("工具被调用: multiply, 参数: a={}, b={}", a, b);
        double result = a * b;
        log.info("计算结果: {}", result);
        return result;
    }

    /**
     * 除法运算
     */
    @Tool(description = "计算两个数字的商,除数不能为0")
    public double divide(
            @ToolParam(description = "被除数") double a,
            @ToolParam(description = "除数,不能为0") double b) {

        log.info("工具被调用: divide, 参数: a={}, b={}", a, b);

        if (b == 0) {
            String error = "除数不能为0";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        double result = a / b;
        log.info("计算结果: {}", result);
        return result;
    }

    /**
     * 计算平方
     */
    @Tool(description = "计算一个数字的平方")
    public double square(
            @ToolParam(description = "要计算平方的数字") double number) {

        log.info("工具被调用: square, 参数: number={}", number);
        double result = number * number;
        log.info("计算结果: {}", result);
        return result;
    }

    /**
     * 计算平方根
     */
    @Tool(description = "计算一个数字的平方根,数字必须为非负数")
    public double squareRoot(
            @ToolParam(description = "要计算平方根的数字,必须>=0") double number) {

        log.info("工具被调用: squareRoot, 参数: number={}", number);

        if (number < 0) {
            String error = "不能计算负数的平方根";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        double result = Math.sqrt(number);
        log.info("计算结果: {}", result);
        return result;
    }
}
