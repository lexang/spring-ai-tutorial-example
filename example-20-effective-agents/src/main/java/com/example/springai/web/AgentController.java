package com.example.springai.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springai.service.AgentWorkflowService;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentWorkflowService workflowService;

    public AgentController(AgentWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/chain")
    public AgentWorkflowService.ChainResult chain(@Valid @RequestBody ChainRequest request) {
        return this.workflowService.chain(request.input(), request.steps());
    }

    @PostMapping("/parallel")
    public AgentWorkflowService.ParallelResult parallel(@Valid @RequestBody ParallelRequest request) {
        return this.workflowService.parallel(request.input(), request.perspectives());
    }

    @PostMapping("/route")
    public AgentWorkflowService.RouteResult route(@Valid @RequestBody TextRequest request) {
        return this.workflowService.route(request.input());
    }

    @PostMapping("/orchestrate")
    public AgentWorkflowService.OrchestrationResult orchestrate(
            @Valid @RequestBody TextRequest request) {
        return this.workflowService.orchestrate(request.input());
    }

    @PostMapping("/evaluate-optimize")
    public AgentWorkflowService.OptimizationResult evaluateOptimize(
            @Valid @RequestBody OptimizeRequest request) {
        return this.workflowService.evaluateAndOptimize(request.input(), request.maxIterations());
    }

    public record TextRequest(@NotBlank(message = "input 不能为空") String input) {
    }

    public record ChainRequest(
            @NotBlank(message = "input 不能为空") String input,
            @NotEmpty(message = "steps 至少需要一项")
            List<@NotBlank(message = "步骤不能为空") String> steps) {
    }

    public record ParallelRequest(
            @NotBlank(message = "input 不能为空") String input,
            @NotEmpty(message = "perspectives 至少需要一项")
            List<@NotBlank(message = "视角不能为空") String> perspectives) {
    }

    public record OptimizeRequest(
            @NotBlank(message = "input 不能为空") String input,
            @Min(value = 1, message = "maxIterations 至少为 1")
            @Max(value = 4, message = "maxIterations 最多为 4")
            int maxIterations) {
    }

}
