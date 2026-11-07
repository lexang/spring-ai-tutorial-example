package com.example.springai;

import java.util.List;

import com.example.springai.model.DeterministicJudgeChatModel;
import com.example.springai.model.TestGenModels.GeneratedTestCase;
import com.example.springai.model.TestGenModels.MockDataEntry;
import com.example.springai.model.TestGenModels.TestGenerationRequest;
import com.example.springai.model.TestGenModels.TestPlan;
import com.example.springai.model.TestGenModels.TestPlanEvaluation;
import com.example.springai.service.TestGenService;
import com.example.springai.support.TestGenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TestGenServiceTest {

    @Autowired
    private TestGenService service;

    @Test
    void generatesTestPlanForGivenClass() {
        TestPlan plan = this.service.generate(new TestGenerationRequest(
                "UserService",
                "public class UserService { boolean login(String username, String password); }",
                "覆盖正常登录和错误密码", 3));

        assertThat(plan.targetClass()).isEqualTo("UserService");
        assertThat(plan.summary()).contains("UserService");
        assertThat(plan.testCases()).hasSize(3);
        GeneratedTestCase first = plan.testCases().get(0);
        assertThat(first.name()).startsWith("userservice_test_");
        assertThat(first.input()).isNotBlank();
        assertThat(first.expectedResult()).isNotBlank();
        assertThat(first.mockData()).isNotEmpty();
    }

    @Test
    void defaultMaxCasesIsThreeWhenOmitted() {
        TestPlan plan = this.service.generate(new TestGenerationRequest(
                "OrderService",
                "public class OrderService { Order create(String userId); }",
                null, null));

        assertThat(plan.testCases()).hasSize(3);
    }

    @Test
    void respectsExplicitMaxCases() {
        TestPlan plan = this.service.generate(new TestGenerationRequest(
                "Repo",
                "public class Repo { void save(String s); }",
                "", 1));

        assertThat(plan.testCases()).hasSize(1);
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> this.service.generate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("请求体不能为空");
    }

    @Test
    void rejectsBlankTargetClass() {
        assertThatThrownBy(() -> this.service.generate(new TestGenerationRequest(
                "  ", "public class A {}", null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("targetClass 不能为空");
    }

    @Test
    void rejectsBlankClassSignature() {
        assertThatThrownBy(() -> this.service.generate(new TestGenerationRequest(
                "A", "   ", null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("classSignature 不能为空");
    }

    @Test
    void rejectsTooLargeSignature() {
        String big = "x".repeat(2001);
        assertThatThrownBy(() -> this.service.generate(new TestGenerationRequest(
                "A", big, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能超过 2000");
    }

    @Test
    void rejectsTooLargeInstruction() {
        String big = "x".repeat(501);
        assertThatThrownBy(() -> this.service.generate(new TestGenerationRequest(
                "A", "public class A {}", big, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能超过 500");
    }

    @Test
    void rejectsMaxCasesOutOfRange() {
        assertThatThrownBy(() -> this.service.generate(new TestGenerationRequest(
                "A", "public class A {}", null, 0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1 到 10");

        assertThatThrownBy(() -> this.service.generate(new TestGenerationRequest(
                "A", "public class A {}", null, 11)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1 到 10");
    }

    @Test
    void evaluatesRelevantPlanAsPassed() {
        TestPlan plan = new TestPlan("UserService",
                "为 UserService 生成 1 个测试用例",
                List.of(new GeneratedTestCase("userservice_test_1",
                        "验证 UserService 的基本路径", "input1", "expected1",
                        List.of(new MockDataEntry("id", "1")))));

        TestPlanEvaluation evaluation = this.service.evaluate(plan);

        assertThat(evaluation.feedback()).isNotBlank();
    }

    @Test
    void rejectsNullPlanForEvaluation() {
        assertThatThrownBy(() -> this.service.evaluate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("测试计划不能为空");
    }

    @Test
    void evaluatesAgainstOriginalSpecificationWithExplicitScope() {
        var specification = new TestGenerationRequest("UserService", "boolean login(String username, String password);", "覆盖空密码", 2);
        var plan = service.generate(specification);
        var evaluation = service.evaluateAgainstSpec(specification, plan);
        assertThat(evaluation.mode()).isEqualTo("contract-demo");
        assertThat(evaluation.scope()).isEqualTo("spec-relevancy");
        assertThatThrownBy(() -> service.evaluateAgainstSpec(new TestGenerationRequest("Other", "void run();", "", 2), plan))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
