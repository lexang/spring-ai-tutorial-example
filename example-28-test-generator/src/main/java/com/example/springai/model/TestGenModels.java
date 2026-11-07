package com.example.springai.model;

import java.util.List;

/**
 * 测试生成器的数据模型。
 *
 * <p>所有结构化输出均使用 record 定义，配合 ChatClient 的 entity 方法转为强类型。
 * 目标类签名和测试指令通过分隔标记注入提示词，避免直接拼接用户输入。</p>
 */
public final class TestGenModels {

    private TestGenModels() {
    }

    /** 单条 Mock 数据条目：字段名 -> 值。 */
    public record MockDataEntry(String field, String value) {
    }

    /** 单个生成的测试用例。 */
    public record GeneratedTestCase(String name, String description, String input,
            String expectedResult, List<MockDataEntry> mockData) {
    }

    /** ChatClient 返回的结构化测试计划。 */
    public record TestPlan(String targetClass, String summary, List<GeneratedTestCase> testCases) {
    }

    /** 测试生成请求。 */
    public record TestGenerationRequest(String targetClass, String classSignature,
            String instruction, Integer maxCases) {
    }

    /** 测试计划的评估结果。 */
    public record TestPlanEvaluation(boolean passed, float score, String feedback, String mode, String scope) {
    }

    public record SpecEvaluationRequest(TestGenerationRequest specification, TestPlan plan) {
    }

}
