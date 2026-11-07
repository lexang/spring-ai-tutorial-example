package com.example.springai.evaluation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

/** 将 HTTP 输入转换为 Spring AI 评估请求的服务。 */
@Service
public class EvaluationService {

    private final RelevancyEvaluator relevancyEvaluator;

    private final FactCheckingEvaluator factCheckingEvaluator;
    private final boolean offline;

    public EvaluationService(RelevancyEvaluator relevancyEvaluator, FactCheckingEvaluator factCheckingEvaluator,
            @Value("${app.evaluation.offline:true}") boolean offline) {
        this.relevancyEvaluator = relevancyEvaluator;
        this.factCheckingEvaluator = factCheckingEvaluator;
        this.offline = offline;
    }

    public Result evaluateRelevancy(String question, List<String> context, String response) {
        EvaluationRequest request = new EvaluationRequest(question, documents(context), response);
        try { return result("relevancy", this.relevancyEvaluator.evaluate(request)); }
        catch (RuntimeException exception) { throw new EvaluationFailureException(exception); }
    }

    public Result evaluateFactChecking(List<String> context, String claim) {
        // 这个重载明确表示没有用户问题；context 会被拼接为 document，claim 是 responseContent。
        EvaluationRequest request = new EvaluationRequest(documents(context), claim);
        try { return result("fact-checking", this.factCheckingEvaluator.evaluate(request)); }
        catch (RuntimeException exception) { throw new EvaluationFailureException(exception); }
    }

    private static List<Document> documents(List<String> context) {
        return context.stream().map(text -> Document.builder().text(text).build()).toList();
    }

    private Result result(String evaluator, EvaluationResponse response) {
        return new Result(evaluator, response.isPass(), response.getScore(), response.getFeedback(),
                response.getMetadata(), offline ? "contract-demo" : "model-judge");
    }

    public record Result(String evaluator, boolean pass, float score, String feedback,
            Map<String, Object> metadata, String mode) {
    }

    public static class EvaluationFailureException extends RuntimeException {
        public EvaluationFailureException(Throwable cause) { super("评估模型执行失败", cause); }
    }

}
