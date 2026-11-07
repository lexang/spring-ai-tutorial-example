package com.example.springai;

import com.example.springai.model.DeterministicEmbeddingModel;
import com.example.springai.model.OfflineKnowledgeChatModel;
import com.example.springai.service.KnowledgeBaseService;
import com.example.springai.service.KnowledgeBaseService.AskRequest;
import com.example.springai.service.KnowledgeBaseService.IngestRequest;
import com.example.springai.support.KnowledgeOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeBaseServiceTest {

    @Test
    void failedDeleteKeepsChunkTrackingForRetry() {
        VectorStore store = org.mockito.Mockito.mock(VectorStore.class);
        KnowledgeBaseService service = new KnowledgeBaseService(store, new OfflineKnowledgeChatModel());
        service.ingest(document("删除重试", "guide://retry", "test", "这是一段足够长的测试文档，用于验证删除失败后仍然能够重新尝试删除。"));
        org.mockito.Mockito.doThrow(new RuntimeException("存储失败")).doNothing()
            .when(store).delete(org.mockito.ArgumentMatchers.anyList());
        assertThatThrownBy(() -> service.deleteBySource("guide://retry"))
            .isInstanceOf(KnowledgeOperationException.class);
        assertThat(service.status().sourceCount()).isEqualTo(1);
        assertThat(service.deleteBySource("guide://retry").deletedChunkCount()).isPositive();
        assertThat(service.status().sourceCount()).isZero();
    }

    @Test
    void ingestAndAskRunTheCompleteOfflineRagPipeline() {
        KnowledgeBaseService service = service(new OfflineKnowledgeChatModel());
        service.ingest(document("RAG 指南", "guide://rag", "spring-ai",
                "Spring AI 使用 VectorStore 保存文档嵌入，QuestionAnswerAdvisor 先检索相关文档，再把上下文加入用户问题。"));

        var answer = service.ask(new AskRequest("QuestionAnswerAdvisor 如何使用 VectorStore？", 1, null));

        assertThat(answer.answer()).contains("根据知识库", "QuestionAnswerAdvisor");
        assertThat(answer.status()).isEqualTo("ANSWERED");
        assertThat(answer.usage().reported()).isFalse();
        assertThat(answer.usage().totalTokens()).isNull();
        assertThat(answer.sources()).singleElement().satisfies(source -> {
            assertThat(source.title()).isEqualTo("RAG 指南");
            assertThat(source.source()).isEqualTo("guide://rag");
            assertThat(source.score()).isNotNull();
        });
    }

    @Test
    void categoryFilterIsPassedThroughAdvisorContext() {
        KnowledgeBaseService service = service(new OfflineKnowledgeChatModel());
        service.ingest(document("Spring AI", "guide://ai", "spring-ai",
                "Spring AI 提供 ChatModel、EmbeddingModel、VectorStore 和 RAG Advisor 等统一抽象。"));
        service.ingest(document("Spring Boot", "guide://boot", "spring-boot",
                "Spring Boot 提供自动配置、Starter、Actuator 和外部化配置等应用开发能力。"));

        var answer = service.ask(new AskRequest("Spring 有哪些能力？", 5, "spring-ai"));

        assertThat(answer.sources()).isNotEmpty().allMatch(source -> source.category().equals("spring-ai"));
        assertThat(answer.answer()).contains("Spring AI");
    }

    @Test
    void ingestingTheSameSourceReplacesItsTrackedChunks() {
        KnowledgeBaseService service = service(new OfflineKnowledgeChatModel());

        var first = service.ingest(document("旧标题", "guide://replace", "spring-ai",
                "第一版文档介绍 Spring AI 的向量检索流程以及基础的 RAG 问答能力。"));
        var second = service.ingest(document("新标题", "guide://replace", "spring-ai",
                "第二版文档说明 QuestionAnswerAdvisor 会把检索上下文加入提示词，并保留来源信息。"));

        assertThat(first.replacedExistingSource()).isFalse();
        assertThat(second.replacedExistingSource()).isTrue();
        assertThat(service.status().sourceCount()).isEqualTo(1);
        assertThat(service.status().chunkCount()).isEqualTo(second.chunkCount());
    }

    @Test
    void deleteRemovesTrackedSourceAndEmptyKnowledgeBaseIsANormalAnswer() {
        KnowledgeBaseService service = service(new OfflineKnowledgeChatModel());
        service.ingest(document("待删除", "guide://delete", "spring-ai",
                "这是一段用于验证知识库删除行为的 Spring AI 文档，删除后不应继续作为检索来源。"));

        var deleted = service.deleteBySource("guide://delete");
        var answer = service.ask(new AskRequest("删除后的文档在哪里？", 3, null));

        assertThat(deleted.deletedChunkCount()).isPositive();
        assertThat(service.status().sourceCount()).isZero();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.answer()).contains("没有足够信息");
        assertThat(answer.status()).isEqualTo("NO_CONTEXT");
    }

    @Test
    void invalidInputsFailBeforeCallingModels() {
        KnowledgeBaseService service = service(new OfflineKnowledgeChatModel());

        assertThatThrownBy(() -> service.ingest(document("标题", "guide://short", "spring-ai", "太短")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("至少需要 20 个字符");
        assertThatThrownBy(() -> service.ask(new AskRequest("问题", 11, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("topK 必须在 1 到 10 之间");
        assertThatThrownBy(() -> service.ask(new AskRequest("问题", 1, "spring-ai' OR true")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("category 只能包含");
    }

    @Test
    void providerFailureBecomesStableChineseOperationError() {
        RuntimeException providerFailure = new RuntimeException("供应商敏感错误");
        ChatModel failingModel = prompt -> {
            throw providerFailure;
        };
        KnowledgeBaseService service = service(failingModel);
        service.ingest(document("失败测试", "guide://failure", "test",
                "该文档用于验证 ChatModel 调用失败时不会把供应商的原始错误直接返回客户端。"));

        assertThatThrownBy(() -> service.ask(new AskRequest("触发模型失败", 1, null)))
            .isInstanceOf(KnowledgeOperationException.class)
            .hasMessage("文档检索或回答生成失败")
            .hasMessageNotContaining("敏感")
            .hasCause(providerFailure);
    }

    private KnowledgeBaseService service(ChatModel chatModel) {
        DeterministicEmbeddingModel embeddingModel = new DeterministicEmbeddingModel();
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        return new KnowledgeBaseService(vectorStore, chatModel);
    }

    private IngestRequest document(String title, String source, String category, String content) {
        return new IngestRequest(title, content, source, category);
    }

}
