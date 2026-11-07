package com.minimax.examples.service;

import java.util.Locale;

import com.minimax.examples.service.ModelClasses.CalculationResult;
import com.minimax.examples.service.ModelClasses.ComplexResult;
import com.minimax.examples.service.ModelClasses.PercentageResult;
import com.minimax.examples.service.ModelClasses.ScientificResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * 计算器 MCP 工具。
 */
@Service
public class CalculatorService {

    @McpTool(name = "basic-calculation", description = "执行加减乘除")
    public CalculationResult basicCalculation(
            @McpToolParam(description = "第一个数字", required = true) double a,
            @McpToolParam(description = "第二个数字", required = true) double b,
            @McpToolParam(description = "运算符：+、-、*、/", required = true) String operation) {
        requireFinite(a, b);
        if (operation == null || operation.isBlank()) { throw new IllegalArgumentException("运算符不能为空"); }
        double result = switch (operation) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> {
                if (b == 0) {
                    throw new IllegalArgumentException("除数不能为零");
                }
                yield a / b;
            }
            default -> throw new IllegalArgumentException("不支持的运算符: " + operation);
        };
        requireFinite(result);
        return new CalculationResult(a, b, operation, result);
    }

    @McpTool(name = "percentage-calculation", description = "计算一个数的百分比")
    public PercentageResult percentageCalculation(
            @McpToolParam(description = "基础数值", required = true) double base,
            @McpToolParam(description = "百分比，范围 0 到 100", required = true) double percentage) {
        requireFinite(base, percentage);
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("百分比必须在 0 到 100 之间");
        }
        double result = base * (percentage / 100);
        requireFinite(result);
        return new PercentageResult(base, percentage, result);
    }

    @McpTool(name = "scientific-calculation", description = "执行 sqrt、log、ln、sin、cos 或 tan")
    public ScientificResult scientificCalculation(
            @McpToolParam(description = "运算类型", required = true) String operation,
            @McpToolParam(description = "输入数值", required = true) double value) {
        requireFinite(value);
        if (operation == null || operation.isBlank()) { throw new IllegalArgumentException("运算符不能为空"); }
        double result = switch (operation.toLowerCase(Locale.ROOT)) {
            case "sqrt" -> {
                if (value < 0) {
                    throw new IllegalArgumentException("平方根的输入不能为负数");
                }
                yield Math.sqrt(value);
            }
            case "log" -> {
                if (value <= 0) {
                    throw new IllegalArgumentException("对数的输入必须大于零");
                }
                yield Math.log10(value);
            }
            case "ln" -> {
                if (value <= 0) {
                    throw new IllegalArgumentException("自然对数的输入必须大于零");
                }
                yield Math.log(value);
            }
            case "sin" -> Math.sin(Math.toRadians(value));
            case "cos" -> Math.cos(Math.toRadians(value));
            case "tan" -> Math.tan(Math.toRadians(value));
            default -> throw new IllegalArgumentException("不支持的科学运算: " + operation);
        };
        requireFinite(result);
        return new ScientificResult(operation, value, result);
    }

    @McpTool(name = "complex-calculation", description = "执行白名单内的简单复合公式")
    public ComplexResult complexCalculation(
            @McpToolParam(description = "变量 a", required = true) double a,
            @McpToolParam(description = "变量 b", required = true) double b,
            @McpToolParam(description = "变量 c", required = true) double c,
            @McpToolParam(description = "公式：(a+b)*c、(a-b)/c 或 a+b+c", required = true) String formula) {
        requireFinite(a, b, c);
        if (formula == null || formula.isBlank()) { throw new IllegalArgumentException("公式不能为空"); }
        double result = switch (formula.replace(" ", "")) {
            case "(a+b)*c" -> (a + b) * c;
            case "(a-b)/c" -> {
                if (c == 0) {
                    throw new IllegalArgumentException("除数不能为零");
                }
                yield (a - b) / c;
            }
            case "a+b+c" -> a + b + c;
            default -> throw new IllegalArgumentException("公式不在允许的白名单中: " + formula);
        };
        requireFinite(result);
        return new ComplexResult(a, b, c, formula, result);
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) { throw new IllegalArgumentException("计算输入和结果必须是有限数值"); }
        }
    }

}
