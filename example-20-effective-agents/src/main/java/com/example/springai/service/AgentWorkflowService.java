package com.example.springai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

/**
 * 五种 Agentic pattern 的最小、可组合实现。
 *
 * <p>官方页面给出的是架构模式；这里把每个模型调用和边界条件显式写成应用代码，
 * 以便测试、限流和审计。</p>
 */
@Service
public class AgentWorkflowService {

    private final ChatClient chatClient;

    private final ExecutorService executor;

    public AgentWorkflowService(ChatClient chatClient, ExecutorService executor) {
        this.chatClient = chatClient;
        this.executor = executor;
    }

    public ChainResult chain(String input, List<String> steps) {
        requireText(input, "input");
        requireSteps(steps);
        String current = input;
        List<StepTrace> trace = new ArrayList<>();
        for (String step : steps) {
            String output = call("[CHAIN]\n步骤：" + step + "\n输入：" + current);
            trace.add(new StepTrace(step, output));
            current = output;
        }
        return new ChainResult(current, trace);
    }

    public ParallelResult parallel(String input, List<String> perspectives) {
        requireText(input, "input");
        requireSteps(perspectives);
        if (perspectives.size() > 8) {
            throw new IllegalArgumentException("parallel perspectives 最多 8 项");
        }
        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();
        List<WorkerResult> workers;
        try {
            for (String perspective : perspectives) {
                futures.add(CompletableFuture.supplyAsync(
                    () -> new WorkerResult(perspective,
                        call("[WORKER]\n任务：" + perspective + "\n输入：" + input)), this.executor));
            }
            workers = futures.stream().map(CompletableFuture::join).toList();
        }
        catch (RejectedExecutionException exception) {
            futures.forEach(future -> future.cancel(true));
            throw new WorkflowBusyException("工作流队列已满，请稍后重试");
        }
        catch (CompletionException exception) {
            futures.forEach(future -> future.cancel(true));
            throw new ModelOperationException("并行模型调用失败", exception);
        }
        String joined = workers.stream()
            .map(worker -> worker.perspective() + "：" + worker.output())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        String summary = call("[AGGREGATE]\n输入：" + input + "\n结果：\n" + joined);
        return new ParallelResult(workers, summary);
    }

    public RouteResult route(String input) {
        requireText(input, "input");
        String route = normalizeRoute(call("[CLASSIFY]\n请把请求分类为 billing、technical 或 general：\n" + input));
        String response = call("[ROUTE:" + route + "]\n请求：" + input);
        return new RouteResult(route, response);
    }

    public OrchestrationResult orchestrate(String task) {
        requireText(task, "task");
        Plan plan = modelOperation(() -> this.chatClient.prompt()
            .user("[PLAN]\n为以下任务生成两个互相独立的子任务，并只返回 JSON：\n" + task)
            .options(ChatOptions.builder().temperature(0.1).maxTokens(800))
            .call()
            .entity(Plan.class));
        if (plan == null || plan.subtasks() == null || plan.subtasks().isEmpty()) {
            throw new ModelOperationException("编排器没有生成可执行子任务");
        }
        try { requireSteps(plan.subtasks()); }
        catch (IllegalArgumentException exception) {
            throw new ModelOperationException("编排器生成的子任务不符合边界", exception);
        }
        ParallelResult workers = parallel(task, plan.subtasks());
        return new OrchestrationResult(plan, workers.workers(), workers.summary());
    }

    public OptimizationResult evaluateAndOptimize(String task, int maxIterations) {
        requireText(task, "task");
        if (maxIterations < 1 || maxIterations > 4) {
            throw new IllegalArgumentException("maxIterations 必须位于 1 到 4 之间");
        }
        String initialDraft = call("[GENERATE]\n任务：" + task);
        String draft = initialDraft;
        List<EvaluationTrace> evaluations = new ArrayList<>();
        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            String currentDraft = draft;
            Evaluation evaluation = modelOperation(() -> this.chatClient.prompt()
                .user("[EVALUATE]\n任务：" + task + "\n当前方案：" + currentDraft
                    + "\n只返回 pass 和 feedback 字段的 JSON。")
                .options(ChatOptions.builder().temperature(0.0).maxTokens(800))
                .call()
                .entity(Evaluation.class));
            if (evaluation == null || evaluation.pass() == null || evaluation.feedback() == null
                    || evaluation.feedback().isBlank() || evaluation.feedback().length() > 4000) {
                throw new ModelOperationException("评审器没有返回有效的 pass 和 feedback");
            }
            evaluations.add(new EvaluationTrace(iteration, evaluation));
            if (evaluation.pass()) {
                return new OptimizationResult(initialDraft, evaluations, draft, iteration, true, "PASSED");
            }
            if (iteration < maxIterations) {
                draft = call("[REFINE]\n任务：" + task + "\n评审意见：" + evaluation.feedback()
                    + "\n当前方案：" + draft);
            }
        }
        return new OptimizationResult(initialDraft, evaluations, draft, maxIterations, false, "ITERATION_LIMIT");
    }

    private String call(String prompt) {
        String result = modelOperation(() -> this.chatClient.prompt()
            .user(prompt)
            .options(ChatOptions.builder().temperature(0.2).maxTokens(800))
            .call()
            .content());
        if (result == null || result.isBlank()) {
            throw new ModelOperationException("模型返回了空内容");
        }
        return result.trim();
    }

    private String normalizeRoute(String route) {
        String normalized = route == null ? "" : route.toLowerCase(Locale.ROOT).trim();
        if (!List.of("billing", "technical", "general").contains(normalized)) {
            throw new ModelOperationException("模型返回了未授权的路由标签");
        }
        return normalized;
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        if (value.length() > 12000) {
            throw new IllegalArgumentException(name + " 最多允许 12000 个字符");
        }
    }

    private void requireSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("步骤列表至少需要一项");
        }
        if (steps.size() > 8) {
            throw new IllegalArgumentException("步骤列表最多允许 8 项");
        }
        if (steps.stream().anyMatch(step -> step == null || step.isBlank())) {
            throw new IllegalArgumentException("步骤内容不能为空");
        }
        if (steps.stream().anyMatch(step -> step.length() > 1000)) {
            throw new IllegalArgumentException("单个步骤最多允许 1000 个字符");
        }
    }

    private <T> T modelOperation(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException exception) {
            throw new ModelOperationException("模型调用或结构化结果转换失败", exception);
        }
    }

    public record ChainResult(String output, List<StepTrace> trace) {
    }

    public record StepTrace(String step, String output) {
    }

    public record ParallelResult(List<WorkerResult> workers, String summary) {
    }

    public record WorkerResult(String perspective, String output) {
    }

    public record RouteResult(String route, String response) {
    }

    public record Plan(List<String> subtasks) {
    }

    public record OrchestrationResult(Plan plan, List<WorkerResult> workers, String finalAnswer) {
    }

    public record Evaluation(Boolean pass, String feedback) {
    }

    public record EvaluationTrace(int iteration, Evaluation evaluation) {
    }

    public record OptimizationResult(String draft, List<EvaluationTrace> evaluations,
            String finalSolution, int iterations, boolean accepted, String stopReason) {
    }

    public static final class ModelOperationException extends RuntimeException {
        public ModelOperationException(String message) { super(message); }
        public ModelOperationException(String message, Throwable cause) { super(message, cause); }
    }

    public static final class WorkflowBusyException extends RuntimeException {
        public WorkflowBusyException(String message) { super(message); }
    }

}
