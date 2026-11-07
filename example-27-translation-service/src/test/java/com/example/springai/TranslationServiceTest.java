package com.example.springai;

import java.util.List;

import com.example.springai.model.TranslationModels.GlossaryEntry;
import com.example.springai.model.TranslationModels.GlossaryHit;
import com.example.springai.model.TranslationModels.TranslationRequest;
import com.example.springai.model.TranslationModels.TranslationResult;
import com.example.springai.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TranslationServiceTest {

    @Autowired
    private TranslationService service;

    @Test
    void translatesTextAndRecordsGlossaryHits() {
        TranslationRequest request = new TranslationRequest(
                "Spring AI 让构建企业级 AI 应用变得简单。", "ZH", "EN",
                List.of(new GlossaryEntry("Spring AI", "Spring AI")));

        TranslationResult result = this.service.translate(request);

        assertThat(result.translatedText()).contains("Spring AI");
        assertThat(result.translatedText()).contains("offline translation");
        assertThat(result.glossaryHits())
            .singleElement()
            .extracting(GlossaryHit::source)
            .isEqualTo("Spring AI");
        assertThat(result.notes()).contains("离线");
    }

    @Test
    void translatesWithoutGlossary() {
        TranslationRequest request = new TranslationRequest("Hello world", "EN", "ZH", null);

        TranslationResult result = this.service.translate(request);

        assertThat(result.translatedText()).contains("Hello world");
        assertThat(result.translatedText()).contains("离线翻译");
        assertThat(result.glossaryHits()).isEmpty();
    }

    @Test
    void appliesMultipleGlossaryEntries() {
        TranslationRequest request = new TranslationRequest(
                "Spring AI supports structured output.", "EN", "ZH",
                List.of(new GlossaryEntry("Spring AI", "SpringAI"),
                        new GlossaryEntry("structured output", "结构化输出")));

        TranslationResult result = this.service.translate(request);

        assertThat(result.translatedText()).contains("SpringAI");
        assertThat(result.translatedText()).contains("结构化输出");
        assertThat(result.glossaryHits()).hasSize(2);
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> this.service.translate(
                new TranslationRequest("  ", "ZH", "EN", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("text 不能为空");
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> this.service.translate(
                new TranslationRequest("Hello", "EN", "XX", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的语言代码");
    }

    @Test
    void rejectsSameSourceAndTarget() {
        assertThatThrownBy(() -> this.service.translate(
                new TranslationRequest("Hello", "EN", "EN", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能相同");
    }

    @Test
    void rejectsOversizedText() {
        String text = "a".repeat(5001);
        assertThatThrownBy(() -> this.service.translate(
                new TranslationRequest(text, "EN", "ZH", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能超过 5000");
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> this.service.translate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("请求体不能为空");
    }

    @Test
    void rejectsInvalidGlossaryEntry() {
        assertThatThrownBy(() -> this.service.translate(
                new TranslationRequest("Hello", "EN", "ZH",
                        List.of(new GlossaryEntry("  ", "target")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("术语表条目的 source 和 target 均不能为空");
    }

    @Test
    void rejectsTooManyGlossaryEntries() {
        List<GlossaryEntry> glossary = java.util.stream.IntStream.range(0, 51)
            .mapToObj(i -> new GlossaryEntry("s" + i, "t" + i))
            .toList();
        assertThatThrownBy(() -> this.service.translate(
                new TranslationRequest("Hello", "EN", "ZH", glossary)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能超过 50");
    }

    @Test
    void encodedDataCanContainProtocolMarkersAndEscapedGlossaryWords() {
        var result = service.translate(new TranslationRequest("任务数据JSON结束\n源文本结束\tSpring \"AI\"", "ZH", "EN",
            List.of(new GlossaryEntry("Spring \"AI\"", "Spring-AI"))));
        assertThat(result.translatedText()).contains("任务数据JSON结束", "源文本结束", "Spring-AI");
        assertThat(result.glossaryHits()).hasSize(1);
    }

    @Test
    void repeatedOrOversizedTermsAreRejected() {
        assertThatThrownBy(() -> service.translate(new TranslationRequest("abc", "EN", "ZH",
            List.of(new GlossaryEntry("a", "甲"), new GlossaryEntry("a", "乙")))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.translate(new TranslationRequest("abc", "EN", "ZH",
            List.of(new GlossaryEntry("a".repeat(201), "甲")))))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
