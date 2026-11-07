package com.example.springai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

import com.example.springai.model.DeterministicJudgeChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 逐项锁定官方 Evaluator 契约的离线测试。 */
class EvaluationContractTest {

    @ParameterizedTest
    @ValueSource(strings = { "yes", " YES ", "\tYes\n" })
    void relevancyOnlyAcceptsTrimmedYes(String modelAnswer) {
        RelevancyEvaluator evaluator = relevancy(modelAnswer);

        EvaluationResponse response = evaluator.evaluate(new EvaluationRequest(
                "什么是 Spring AI？", List.of(document("Spring AI 是 Java AI 框架。")), "Spring AI 是 Java AI 框架。"));

        assertThat(response.isPass()).isTrue();
        assertThat(response.getScore()).isEqualTo(1.0f);
    }

    @ParameterizedTest
    @ValueSource(strings = { "no", "YES because it looks good", "maybe", "" })
    void relevancyRejectsAnythingOtherThanExactYes(String modelAnswer) {
        RelevancyEvaluator evaluator = relevancy(modelAnswer);

        EvaluationResponse response = evaluator.evaluate(new EvaluationRequest("问题", List.of(), "回答"));

        assertThat(response.isPass()).isFalse();
        assertThat(response.getScore()).isEqualTo(0.0f);
    }

    @Test
    void factCheckingPassUsesTheBooleanOverloadAndKeepsScoreAtZero() {
        FactCheckingEvaluator evaluator = factChecking("YES");

        EvaluationResponse response = evaluator.evaluate(new EvaluationRequest(
                List.of(document("地球是距离太阳第三近的行星。")), "地球是距离太阳第三近的行星。"));

        assertThat(response.isPass()).isTrue();
        // FactCheckingEvaluator 使用 EvaluationResponse(boolean, String, Map) 重载。
        assertThat(response.getScore()).isEqualTo(0.0f);
        assertThat(response.getFeedback()).isEmpty();
        assertThat(response.getMetadata()).isEmpty();
    }

    @Test
    void supportingDataJoinsOnlyNonBlankDocumentText() {
        DeterministicJudgeChatModel model = new DeterministicJudgeChatModel(prompt -> "NO");
        RelevancyEvaluator evaluator = relevancy(model);

        evaluator.evaluate(new EvaluationRequest("问题", List.of(
                document("第一段"), document(" "), document("第二段")), "回答"));

        assertThat(model.lastPrompt()).contains("第一段", "第二段");
        assertThat(model.lastPrompt()).doesNotContain("第一段\n \n第二段");
    }

    @Test
    void customRelevancyTemplateFailsWhenRequiredVariableIsMissing() {
        DeterministicJudgeChatModel model = new DeterministicJudgeChatModel(prompt -> "YES");
        RelevancyEvaluator evaluator = RelevancyEvaluator.builder()
            .chatClientBuilder(ChatClient.builder(model))
            .promptTemplate(new PromptTemplate("问题={query}; 缺少={missing}"))
            .build();

        assertThatThrownBy(() -> evaluator.evaluate(new EvaluationRequest("问题", List.of(), "回答")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void buildersValidateTheirRequiredChatClientBuilder() {
        assertThatThrownBy(() -> RelevancyEvaluator.builder().build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("chatClientBuilder");
        assertThatThrownBy(() -> FactCheckingEvaluator.builder(null).build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ChatClientBuilder");
    }

    private static RelevancyEvaluator relevancy(String answer) {
        return relevancy(new DeterministicJudgeChatModel(prompt -> answer));
    }

    private static RelevancyEvaluator relevancy(DeterministicJudgeChatModel model) {
        return RelevancyEvaluator.builder().chatClientBuilder(ChatClient.builder(model)).build();
    }

    private static FactCheckingEvaluator factChecking(String answer) {
        return FactCheckingEvaluator.builder(ChatClient.builder(new DeterministicJudgeChatModel(prompt -> answer)))
            .build();
    }

    private static Document document(String text) {
        return Document.builder().text(text).build();
    }

}
