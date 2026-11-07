package com.example.springai.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springai.service.PerformanceService;

@RestController
@RequestMapping("/api/performance")
@Validated
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @PostMapping("/estimate")
    public PerformanceService.Estimate estimate(@Valid @RequestBody EstimateRequest request) {
        return this.performanceService.estimate(request.text(), request.maxOutputTokens());
    }

    @PostMapping("/chat")
    public PerformanceService.ChatResult chat(@Valid @RequestBody ChatRequest request) {
        return this.performanceService.chat(
            request.prompt(), request.maxOutputTokens(), request.maxBudget());
    }

    @PostMapping("/batch")
    public PerformanceService.BatchPlan batch(@Valid @RequestBody BatchRequest request) {
        return this.performanceService.batch(
            request.documents(), request.maxInputTokens(), request.reservePercentage());
    }

    @GetMapping("/usage")
    public PerformanceService.UsageReport usage() {
        return this.performanceService.cumulativeUsage();
    }

    public record EstimateRequest(
            @NotBlank(message = "text 不能为空") String text,
            @Min(value = 1, message = "maxOutputTokens 必须大于 0") int maxOutputTokens) {
    }

    public record ChatRequest(
            @NotBlank(message = "prompt 不能为空") String prompt,
            @Min(value = 1, message = "maxOutputTokens 必须大于 0") int maxOutputTokens,
            @DecimalMin(value = "0.0", message = "maxBudget 不能为负数")
            BigDecimal maxBudget) {
    }

    public record BatchRequest(
            @NotEmpty(message = "documents 至少需要一项")
            List<@NotBlank(message = "文档内容不能为空") String> documents,
            @Min(value = 1, message = "maxInputTokens 必须大于 0") int maxInputTokens,
            @DecimalMin(value = "0.0", inclusive = true, message = "reservePercentage 不能为负数")
            @DecimalMax(value = "0.99", message = "reservePercentage 必须小于 1")
            double reservePercentage) {
    }

}
