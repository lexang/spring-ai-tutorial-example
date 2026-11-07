package com.example.springai;

import com.example.springai.model.DeterministicEmbeddingModel;
import com.example.springai.model.OfflineCustomerChatModel;
import com.example.springai.service.CustomerSupportService;
import com.example.springai.service.CustomerSupportService.ChatRequest;
import com.example.springai.tool.CustomerOrderTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerSupportServiceTest {

    @Test
    void concurrentChatAndClearShareOneScopedLock() throws Exception {
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var peak = new java.util.concurrent.atomic.AtomicInteger();
        var fixed = new OfflineCustomerChatModel();
        org.springframework.ai.chat.model.ChatModel observed = prompt -> {
            int count = active.incrementAndGet();
            peak.accumulateAndGet(count, Math::max);
            try { Thread.yield(); return fixed.call(prompt); }
            finally { active.decrementAndGet(); }
        };
        var concurrent = new CustomerSupportService(this.memory, observed,
            SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build(), new CustomerOrderTools());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < 40; index++) {
                boolean clear = index % 3 == 0;
                tasks.add(executor.submit(() -> {
                    if (clear) concurrent.clear("customer-a", "concurrent");
                    else concurrent.chat(new ChatRequest("customer-a", "concurrent", "如何退款？"));
                }));
            }
            for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(peak).hasValue(1);
            assertThat(concurrent.status().trackedConversationLocks()).isZero();
        }
        finally { executor.shutdownNow(); }
    }

    @Test
    void completedOperationsReleaseLockEntriesAndProviderArgumentErrorsAreNotClientErrors() {
        this.service.chat(new ChatRequest("customer-a", "lock-release", "如何退款？"));
        assertThat(this.service.status().trackedConversationLocks()).isZero();
        this.service.clear("customer-a", "lock-release");
        assertThat(this.service.status().trackedConversationLocks()).isZero();
        var failing = new CustomerSupportService(this.memory, prompt -> {
            throw new IllegalArgumentException("供应商敏感详情");
        }, SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build(), new CustomerOrderTools());
        assertThatThrownBy(() -> failing.chat(new ChatRequest("customer-a", "failure", "如何退款？")))
            .isInstanceOf(com.example.springai.support.CustomerSupportOperationException.class);
    }

    private ChatMemory memory;

    private OfflineCustomerChatModel chatModel;

    private CustomerSupportService service;

    @BeforeEach
    void setUp() {
        this.memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        this.chatModel = new OfflineCustomerChatModel();
        var vectorStore = SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build();
        this.service = new CustomerSupportService(this.memory, this.chatModel,
                vectorStore, new CustomerOrderTools());
    }

    @Test
    void faqQuestionUsesRagAndStoresFinalExchange() {
        var result = this.service.chat(new ChatRequest(
                "customer-a", "faq-case", "已经发货的订单如何退款？"));

        assertThat(result.answer()).contains("客服知识库", "退货");
        assertThat(result.sources()).isNotEmpty();
        assertThat(this.memory.get("customer-a:faq-case"))
            .extracting(message -> message.getMessageType().getValue())
            .containsExactly("user", "assistant");
    }

    @Test
    void orderQuestionRunsAutomaticToolLoopWithTrustedCustomerContext() {
        int callsBefore = this.chatModel.getCallCount();

        var result = this.service.chat(new ChatRequest(
                "customer-a", "order-case", "请查询订单 ORD-1001 的物流状态"));

        assertThat(result.answer()).contains("已发货", "SF123456789");
        assertThat(this.chatModel.getCallCount() - callsBefore).isEqualTo(2);
        assertThat(this.memory.get("customer-a:order-case"))
            .extracting(message -> message.getMessageType().getValue())
            .containsExactly("user", "assistant");
    }

    @Test
    void toolDoesNotExposeAnotherCustomersOrder() {
        var result = this.service.chat(new ChatRequest(
                "customer-b", "order-case", "请查询订单 ORD-1001 的状态"));

        assertThat(result.answer()).contains("未找到该客户的订单");
        assertThat(result.answer()).doesNotContain("SF123456789");
    }

    @Test
    void samePublicConversationIdIsScopedByCustomer() {
        this.service.chat(new ChatRequest("customer-a", "same-case", "如何退款？"));
        this.service.chat(new ChatRequest("customer-b", "same-case", "多久发货？"));

        assertThat(this.memory.get("customer-a:same-case")).hasSize(2);
        assertThat(this.memory.get("customer-b:same-case")).hasSize(2);
        assertThat(this.memory.get("customer-a:same-case").get(0).getText()).contains("退款");
        assertThat(this.memory.get("customer-b:same-case").get(0).getText()).contains("发货");
    }

    @Test
    void clearOnlyRemovesScopedConversation() {
        this.service.chat(new ChatRequest("customer-a", "clear-case", "如何退款？"));
        this.service.chat(new ChatRequest("customer-b", "clear-case", "多久发货？"));

        var result = this.service.clear("customer-a", "clear-case");

        assertThat(result.deletedMessages()).isEqualTo(2);
        assertThat(this.memory.get("customer-a:clear-case")).isEmpty();
        assertThat(this.memory.get("customer-b:clear-case")).hasSize(2);
    }

    @Test
    void invalidIdentifiersAndMessageAreRejected() {
        assertThatThrownBy(() -> this.service.chat(new ChatRequest(
                "customer/a", "case", "测试")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("customerId 只能包含");
        assertThatThrownBy(() -> this.service.chat(new ChatRequest(
                "customer-a", "case", " ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message 不能为空");
    }

}
