package com.example.promptengineering;

import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 需要外部模型的示例。只有启用 live profile 时才注册。
 */
@Profile("live")
@RestController
@RequestMapping("/api/live")
public class LivePromptController {

    private final ChatClient chatClient;
    private final PromptPatternCatalog catalog;

    public LivePromptController(ChatClient.Builder builder, PromptPatternCatalog catalog) {
        this.chatClient = builder.build();
        this.catalog = catalog;
    }

    @PostMapping("/zero-shot")
    public String zeroShot(@RequestBody ReviewRequest request) {
        return call(catalog.zeroShot(request.review()), 0.1, 8);
    }

    @PostMapping("/step-back")
    public String stepBack(@RequestBody QuestionRequest request) {
        PromptPatternCatalog.StepBackPlan plan = catalog.stepBack(request.question());
        String background = call(plan.backgroundPrompt(), 0.2, 300);
        return call(catalog.completeStepBack(plan, background), 0.2, 500);
    }

    @PostMapping("/self-consistency")
    public SamplingResult selfConsistency(@RequestBody SelfConsistencyRequest request) {
        int samples = request.samples();
        if (samples < 1 || samples > 7) {
            throw new IllegalArgumentException("samples 必须位于 1 到 7 之间");
        }
        Prompt prompt = catalog.selfConsistencySample(request.problem());
        List<String> answers = new java.util.ArrayList<>();
        int invalidSamples = 0;
        for (int i = 0; i < samples; i++) {
            String answer = normalizeFinalAnswer(call(prompt, 0.8, 200));
            if (answer == null) {
                invalidSamples++;
            }
            else {
                answers.add(answer);
            }
        }
        if (answers.isEmpty()) {
            throw new ModelOperationException("所有采样结果均缺少有效 FINAL 答案");
        }
        return new SamplingResult(samples, invalidSamples, List.copyOf(answers), catalog.vote(answers));
    }

    static String normalizeFinalAnswer(String answer) {
        for (String line : answer.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("FINAL=")) {
                String value = trimmed.substring(6).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private String call(Prompt prompt, double temperature, int maxTokens) {
        try {
            String content = chatClient.prompt(prompt)
                    .options(ChatOptions.builder().temperature(temperature).maxTokens(maxTokens))
                    .call().content();
            if (content == null || content.isBlank()) {
                throw new ModelOperationException("模型返回了空内容");
            }
            return content.trim();
        }
        catch (ModelOperationException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new ModelOperationException("模型调用失败", exception);
        }
    }

    public record SamplingResult(int requestedSamples, int invalidSamples, List<String> samples,
            PromptPatternCatalog.VoteResult vote) {
    }

    public static final class ModelOperationException extends RuntimeException {
        public ModelOperationException(String message) { super(message); }
        public ModelOperationException(String message, Throwable cause) { super(message, cause); }
    }

    public record ReviewRequest(String review) {
    }

    public record QuestionRequest(String question) {
    }

    public record SelfConsistencyRequest(String problem, int samples) {
    }

}
