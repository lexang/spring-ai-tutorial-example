package com.example.springai.ragmodules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.springai.ragmodules.postretrieval.TopDocumentPostProcessor;
import com.example.springai.ragmodules.retrieval.DocumentJoinService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentPipelineTests {

    @Test
    void shouldKeepFirstDuplicateScoreAndSortByScoreDescending() {
        Document first = document("shared", "第一次出现", 0.40);
        Document duplicate = document("shared", "重复文档", 0.95);
        Document high = document("high", "高分文档", 0.80);
        Document low = document("low", "低分文档", 0.20);
        Map<Query, List<List<Document>>> results = new LinkedHashMap<>();
        results.put(new Query("query-1"), List.of(List.of(first, high)));
        results.put(new Query("query-2"), List.of(List.of(duplicate, low)));

        List<Document> joined = new DocumentJoinService().joinDocuments(results);

        assertThat(joined).extracting(Document::getId).containsExactly("high", "shared", "low");
        assertThat(joined.get(1).getText()).isEqualTo("第一次出现");
        assertThat(joined.get(1).getScore()).isEqualTo(0.40);
    }

    @Test
    void shouldLimitDocumentsWithoutChangingOrder() {
        List<Document> documents = List.of(
                document("one", "一", 0.90),
                document("two", "二", 0.80),
                document("three", "三", 0.70));

        List<Document> processed = new TopDocumentPostProcessor(2)
                .process(new Query("测试"), documents);

        assertThat(processed).extracting(Document::getId).containsExactly("one", "two");
    }

    @Test
    void shouldApplyDocumentedEmptyContextBehavior() {
        Query original = new Query("模型自己的知识是什么？");

        Query strict = ContextualQueryAugmenter.builder().build().augment(original, List.of());
        Query flexible = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build()
                .augment(original, List.of());

        assertThat(strict.text()).isNotEqualTo(original.text());
        assertThat(flexible).isEqualTo(original);
    }

    @Test
    void shouldUseCustomDocumentFormatterAndReturnTextOnlyQuery() {
        Query original = Query.builder()
                .text("什么是 RAG？")
                .history(new UserMessage("前一条消息"))
                .context(Map.of("tenant", "demo"))
                .build();
        PromptTemplate template = PromptTemplate.builder()
                .template("资料：{context}\n问题：{query}")
                .build();
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(template)
                .documentFormatter(documents -> "共 " + documents.size() + " 篇")
                .build();

        Query augmented = augmenter.augment(original, List.of(document("one", "正文", 0.9)));

        assertThat(augmented.text())
                .contains("资料：共 1 篇")
                .contains("问题：什么是 RAG？");
        assertThat(augmented.history()).isEmpty();
        assertThat(augmented.context()).isEmpty();
    }

    @Test
    void shouldRejectContextTemplateWithoutRequiredPlaceholder() {
        PromptTemplate invalidTemplate = PromptTemplate.builder()
                .template("只保留问题：{query}")
                .build();

        assertThatThrownBy(() -> ContextualQueryAugmenter.builder()
                .promptTemplate(invalidTemplate)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context");
    }

    @Test
    void shouldTreatNullScoreAsZeroWhenJoining() {
        Document positive = document("positive", "正分文档", 0.2);
        Document noScore = Document.builder()
                .id("no-score")
                .text("无分数文档")
                .metadata(Map.of("source", "test"))
                .build();
        Map<Query, List<List<Document>>> results = Map.of(
                new Query("query"), List.of(List.of(noScore, positive)));

        List<Document> joined = new DocumentJoinService().joinDocuments(results);

        assertThat(joined).extracting(Document::getId).containsExactly("positive", "no-score");
    }

    @Test
    void shouldRejectNonPositivePostProcessorLimit() {
        assertThatThrownBy(() -> new TopDocumentPostProcessor(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("最大文档数必须大于 0");
    }

    private Document document(String id, String text, double score) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of("source", "test"))
                .score(score)
                .build();
    }
}
