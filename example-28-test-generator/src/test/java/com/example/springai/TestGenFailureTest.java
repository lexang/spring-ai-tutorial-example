package com.example.springai;

import java.util.List;

import com.example.springai.model.DeterministicJudgeChatModel;
import com.example.springai.model.TestGenModels.GeneratedTestCase;
import com.example.springai.model.TestGenModels.TestGenerationRequest;
import com.example.springai.model.TestGenModels.TestPlan;
import com.example.springai.service.TestGenService;
import com.example.springai.support.TestGenException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 直接构造 TestGenService，注入可控的 ChatModel 与离线 RelevancyEvaluator，
 * 验证模型异常、空计划、用例不完整均被包装为 TestGenException（即 HTTP 502）。
 */
class TestGenFailureTest {

    private final RelevancyEvaluator offlineEvaluator = RelevancyEvaluator.builder()
        .chatClientBuilder(ChatClient.builder(new DeterministicJudgeChatModel()))
        .build();

    @Test
    void modelFailureIsWrappedAs502() {
        ChatModel failingModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("供应商内部错误");
            }
        };
        TestGenService service = new TestGenService(ChatClient.builder(failingModel).build(),
                this.offlineEvaluator);

        assertThatThrownBy(() -> service.generate(new TestGenerationRequest(
                "A", "public class A {}", null, 1)))
            .isInstanceOf(TestGenException.class)
            .hasMessage("测试用例生成失败");
    }

    @Test
    void emptyPlanTriggers502() {
        ChatModel emptyModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"targetClass\":\"A\",\"summary\":\"\",\"testCases\":[]}"))));
        TestGenService service = new TestGenService(ChatClient.builder(emptyModel).build(),
                this.offlineEvaluator);

        assertThatThrownBy(() -> service.generate(new TestGenerationRequest(
                "A", "public class A {}", null, 1)))
            .isInstanceOf(TestGenException.class)
            .hasMessageContaining("测试计划未包含任何测试用例");
    }

    @Test
    void incompleteTestCaseTriggers502() {
        ChatModel incompleteModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"targetClass\":\"A\",\"summary\":\"s\",\"testCases\":["
                + "{\"name\":\"\",\"description\":\"\",\"input\":\"\",\"expectedResult\":\"\",\"mockData\":[]}]}"))));
        TestGenService service = new TestGenService(ChatClient.builder(incompleteModel).build(),
                this.offlineEvaluator);

        assertThatThrownBy(() -> service.generate(new TestGenerationRequest(
                "A", "public class A {}", null, 1)))
            .isInstanceOf(TestGenException.class)
            .hasMessageContaining("测试用例字段不完整");
    }

    @Test
    void evaluatorFailureIsWrappedAs502() {
        ChatModel goodModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"targetClass\":\"A\",\"summary\":\"s\",\"testCases\":["
                + "{\"name\":\"n\",\"description\":\"d\",\"input\":\"i\",\"expectedResult\":\"e\",\"mockData\":[]}]}"))));
        RelevancyEvaluator failingEvaluator = new RelevancyEvaluator(
                ChatClient.builder(new ChatModel() {
                    @Override
                    public ChatResponse call(Prompt prompt) {
                        throw new IllegalStateException("评估模型失败");
                    }
                }));
        TestGenService service = new TestGenService(ChatClient.builder(goodModel).build(),
                failingEvaluator);

        assertThatThrownBy(() -> service.evaluate(new TestPlan("A", "s",
                List.of(new GeneratedTestCase("n", "d", "i", "e", List.of())))))
            .isInstanceOf(TestGenException.class)
            .hasMessage("测试计划评估失败");
    }

}
