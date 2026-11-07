package com.example.springai.web;

import com.example.springai.model.TestGenModels.TestGenerationRequest;
import com.example.springai.model.TestGenModels.TestPlan;
import com.example.springai.model.TestGenModels.TestPlanEvaluation;
import com.example.springai.model.TestGenModels.SpecEvaluationRequest;
import com.example.springai.service.TestGenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试生成器 HTTP 端点。
 *
 * <p>POST /api/test-gen/generate 根据目标类签名生成测试计划；
 * POST /api/test-gen/evaluate 对已有测试计划执行相关性评估。</p>
 */
@RestController
@RequestMapping("/api/test-gen")
public class TestGenController {

    private final TestGenService testGenService;

    public TestGenController(TestGenService testGenService) {
        this.testGenService = testGenService;
    }

    @PostMapping("/generate")
    public TestPlan generate(@RequestBody TestGenerationRequest request) {
        return this.testGenService.generate(request);
    }

    @PostMapping("/evaluate")
    public TestPlanEvaluation evaluate(@RequestBody TestPlan plan) {
        return this.testGenService.evaluate(plan);
    }

    @PostMapping("/evaluate-spec")
    public TestPlanEvaluation evaluateSpec(@RequestBody SpecEvaluationRequest request) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        return this.testGenService.evaluateAgainstSpec(request.specification(), request.plan());
    }

}
