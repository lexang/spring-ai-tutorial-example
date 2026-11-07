package com.example.springai;

import com.example.springai.model.DeterministicCodeChatModel;
import com.example.springai.service.CodeEngineeringService;
import com.example.springai.service.CodeEngineeringService.CodeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeEngineeringServiceTest {

    private CodeEngineeringService service;

    @BeforeEach
    void setUp() {
        this.service = new CodeEngineeringService(new DeterministicCodeChatModel());
    }

    @Test
    void reviewParsesTypedStructuredOutput() {
        var result = this.service.review(new CodeRequest("java", "return value;"));

        assertThat(result.summary()).contains("输入校验");
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo("HIGH");
            assertThat(issue.line()).isEqualTo(1);
        });
        assertThat(result.score()).isEqualTo(72);
    }

    @Test
    void testPlanCoversDeterministicBoundaries() {
        var result = this.service.generateTests(new CodeRequest("java", "return value;"));

        assertThat(result.framework()).isEqualTo("JUnit 5");
        assertThat(result.testCases()).contains("空输入被拒绝", "异常被转换为业务错误");
    }

    @Test
    void implementationContainsWarningsAndCode() {
        var result = this.service.generateImplementation(new CodeRequest("java", "实现 normalize"));

        assertThat(result.code()).contains("normalize");
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    void unsupportedLanguageIsRejected() {
        assertThatThrownBy(() -> this.service.review(new CodeRequest("cobol", "DISPLAY value")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("language 仅支持");
    }

    @Test
    void credentialsAreRejectedBeforeModelCall() {
        assertThatThrownBy(() -> this.service.review(new CodeRequest("java", "password=secret")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("疑似包含凭据");
    }

    @Test
    void oversizedCodeIsRejected() {
        assertThatThrownBy(() -> this.service.review(new CodeRequest("java", "x".repeat(30_001))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("长度不能超过");
    }

    @Test
    void supportedLanguagesChooseTheirOwnFrameworkAndImplementation() {
        assertThat(service.generateTests(new CodeRequest("python", "return value")).framework()).isEqualTo("pytest");
        assertThat(service.generateTests(new CodeRequest("typescript", "return value;")).framework()).isEqualTo("Vitest");
        assertThat(service.generateImplementation(new CodeRequest("python", "实现 normalize")).language()).isEqualTo("python");
        assertThat(service.generateImplementation(new CodeRequest("java", "实现 normalize")).code())
            .contains("public final class Normalizer", "? \"\" :").doesNotContain("EMPTY");
    }

    @Test
    void schemaConformingButInvalidReviewIsRejectedByBusinessChecks() {
        var invalid = new CodeEngineeringService(prompt -> new org.springframework.ai.chat.model.ChatResponse(
            java.util.List.of(new org.springframework.ai.chat.model.Generation(
                new org.springframework.ai.chat.messages.AssistantMessage("{\"summary\":\"审查\",\"issues\":[{\"severity\":\"UNKNOWN\",\"line\":99,\"message\":\"问题\",\"suggestion\":\"修改\"}],\"score\":150,\"suggestions\":[]}")))));
        assertThatThrownBy(() -> invalid.review(new CodeRequest("java", "return value;")))
            .isInstanceOf(com.example.springai.support.CodeEngineeringException.class);
    }

}
