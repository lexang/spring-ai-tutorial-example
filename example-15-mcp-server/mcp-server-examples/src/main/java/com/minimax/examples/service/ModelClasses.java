package com.minimax.examples.service;

/**
 * MCP 工具返回的稳定数据模型。
 */
public final class ModelClasses {

    private ModelClasses() {
    }

    public record CalculationResult(double a, double b, String operation, double result) {
    }

    public record PercentageResult(double base, double percentage, double result) {
    }

    public record ScientificResult(String operation, double input, double result) {
    }

    public record ComplexResult(double a, double b, double c, String formula, double result) {
    }

    public record FileListResult(java.util.List<String> fileNames) {
    }

    public record WriteFileResult(String fileName, int bytesWritten, String message) {
    }

}
