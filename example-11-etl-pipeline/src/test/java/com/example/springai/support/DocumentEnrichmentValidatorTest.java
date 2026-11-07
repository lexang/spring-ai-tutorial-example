package com.example.springai.support;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentEnrichmentValidatorTest {

    @Test
    void acceptsNonBlankKeywordMetadata() {
        Document document = new Document("正文");
        document.getMetadata().put("excerpt_keywords", "Spring AI, ETL");

        DocumentEnrichmentValidator.requireKeywords(List.of(document));
    }

    @Test
    void rejectsMissingKeywordMetadata() {
        assertThatThrownBy(() -> DocumentEnrichmentValidator.requireKeywords(List.of(new Document("正文"))))
                .isInstanceOf(EtlOperationException.class)
                .hasMessageContaining("关键词增强未返回有效内容");
    }

    @Test
    void rejectsBlankCurrentSummary() {
        Document document = new Document("正文");
        document.getMetadata().put("section_summary", " ");

        assertThatThrownBy(() -> DocumentEnrichmentValidator.requireCurrentSummaries(List.of(document)))
                .isInstanceOf(EtlOperationException.class)
                .hasMessageContaining("摘要增强未返回有效内容");
    }

}
