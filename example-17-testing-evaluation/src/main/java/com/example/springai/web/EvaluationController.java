package com.example.springai.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springai.evaluation.EvaluationService;
import com.example.springai.evaluation.EvaluationService.Result;

/** 评估演示接口。 */
@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/relevancy")
    public ResponseEntity<Result> relevancy(@Valid @RequestBody RelevancyRequest request) {
        return ResponseEntity.ok(this.evaluationService.evaluateRelevancy(request.question(), request.context(),
                request.response()));
    }

    @PostMapping("/fact-checking")
    public ResponseEntity<Result> factChecking(@Valid @RequestBody FactCheckingRequest request) {
        return ResponseEntity.ok(this.evaluationService.evaluateFactChecking(request.context(), request.claim()));
    }

    @GetMapping("/demo")
    public DemoResponse demo() {
        List<String> context = List.of("Spring AI 2.0 使用 ChatClient 构建对话应用。");
        Result relevant = this.evaluationService.evaluateRelevancy("Spring AI 2.0 如何构建对话应用？", context,
                "Spring AI 2.0 使用 ChatClient 构建对话应用。");
        Result unsupported = this.evaluationService.evaluateFactChecking(
                List.of("地球是距离太阳第三近的行星。"), "地球是距离太阳第四近的行星。");
        return new DemoResponse(relevant, unsupported,
                "contract-demo".equals(relevant.mode())
                        ? "离线 Judge 只用于验证评估器契约，不代表真实语义判断能力。"
                        : "真实 Judge 会产生模型请求，模型判断仍需要基准样本和人工复核。");
    }

    public record RelevancyRequest(
            @NotBlank(message = "问题不能为空") @Size(max = 4000, message = "问题不能超过 4000 字符") String question,
            @NotEmpty(message = "上下文至少需要一项") @Size(max = 20, message = "上下文最多 20 项")
                    List<@NotBlank(message = "上下文内容不能为空") @Size(max = 8000, message = "每项上下文最多 8000 字符") String> context,
            @NotBlank(message = "待评估回答不能为空") @Size(max = 8000, message = "回答不能超过 8000 字符") String response) {
    }

    public record FactCheckingRequest(
            @NotEmpty(message = "文档上下文至少需要一项") @Size(max = 20, message = "上下文最多 20 项")
                    List<@NotBlank(message = "文档内容不能为空") @Size(max = 8000, message = "每项上下文最多 8000 字符") String> context,
            @NotBlank(message = "待核验声明不能为空") @Size(max = 8000, message = "声明不能超过 8000 字符") String claim) {
    }

    public record DemoResponse(Result relevant, Result unsupported, String note) {
    }

}
