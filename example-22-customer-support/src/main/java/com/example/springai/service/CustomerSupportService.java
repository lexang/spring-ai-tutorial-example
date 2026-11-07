package com.example.springai.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.example.springai.support.CustomerSupportOperationException;
import com.example.springai.tool.CustomerOrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CustomerSupportService {

    private static final int MAX_TEXT_LENGTH = 1_000;

    private final ChatMemory chatMemory;

    private final ChatModel chatModel;

    private final CustomerOrderTools orderTools;

    private final ChatClient chatClient;

    private final Map<String, LockEntry> conversationLocks = new ConcurrentHashMap<>();

    public CustomerSupportService(ChatMemory chatMemory, ChatModel chatModel,
            VectorStore vectorStore, CustomerOrderTools orderTools) {
        this.chatMemory = chatMemory;
        this.chatModel = chatModel;
        this.orderTools = orderTools;
        seedKnowledgeBase(vectorStore);
        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem("你是客服助手。知识问题仅依据检索上下文；订单问题必须使用工具；不确定时建议转人工。")
            .defaultAdvisors(
                    QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(3).similarityThreshold(0.0).build())
                        .build(),
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    // 2.0.0 发布构建显式加入 ToolCallingAdvisor；其 ToolAdvisor 标记会抑制重复自动注册。
                    ToolCallingAdvisor.builder().disableInternalConversationHistory().build())
            .build();
    }

    public ChatResult chat(ChatRequest request) {
        ValidatedChat validated = validate(request);
        String scopedConversationId = scopedConversationId(validated.customerId(), validated.conversationId());
        return withConversationLock(scopedConversationId, () -> {
          try {
            ChatClientResponse response = this.chatClient.prompt()
                .user(validated.message())
                .tools(this.orderTools)
                .toolContext(Map.of(CustomerOrderTools.CUSTOMER_ID, validated.customerId()))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId))
                .call()
                .chatClientResponse();
            return toResult(validated.conversationId(), response);
        }
        catch (RuntimeException exception) {
            throw new CustomerSupportOperationException("客服检索、工具调用或回答生成失败", exception);
        }
        });
    }

    public ClearResult clear(String customerIdValue, String conversationIdValue) {
        String customerId = requireIdentifier(customerIdValue, "customerId");
        String conversationId = requireIdentifier(conversationIdValue, "conversationId");
        String scopedConversationId = scopedConversationId(customerId, conversationId);
        return withConversationLock(scopedConversationId, () -> {
          try {
            int deletedMessages = this.chatMemory.get(scopedConversationId).size();
            this.chatMemory.clear(scopedConversationId);
            return new ClearResult(conversationId, deletedMessages);
        }
        catch (RuntimeException exception) {
            throw new CustomerSupportOperationException("清理客服会话失败", exception);
        }
        });
    }

    /** 引用数在 Map.compute 内更新，等待者在加锁前已登记，避免移除仍被使用的锁。 */
    private <T> T withConversationLock(String id, Supplier<T> operation) {
        LockEntry entry = this.conversationLocks.compute(id, (key, current) -> {
            LockEntry selected = current == null ? new LockEntry() : current;
            selected.references++;
            return selected;
        });
        entry.lock.lock();
        try { return operation.get(); }
        finally {
            entry.lock.unlock();
            this.conversationLocks.compute(id, (key, current) -> {
                if (current != entry) throw new IllegalStateException("会话锁引用状态不一致");
                return --current.references == 0 ? null : current;
            });
        }
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    public Status status() {
        return new Status(this.conversationLocks.size(), this.chatModel.getClass().getSimpleName());
    }

    private ChatResult toResult(String conversationId, ChatClientResponse response) {
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null
                || !StringUtils.hasText(response.chatResponse().getResult().getOutput().getText())) {
            throw new CustomerSupportOperationException("回答模型没有返回可用文本");
        }
        Object retrieved = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        List<Document> documents = retrieved instanceof List<?> values
                ? values.stream().filter(Document.class::isInstance).map(Document.class::cast).toList()
                : List.of();
        List<Source> sources = documents.stream().map(document -> new Source(
                document.getMetadata().getOrDefault("title", "未命名文档").toString(),
                document.getMetadata().getOrDefault("source", "unknown").toString(),
                document.getScore())).toList();
        return new ChatResult(conversationId, response.chatResponse().getResult().getOutput().getText(), sources);
    }

    private ValidatedChat validate(ChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String customerId = requireIdentifier(request.customerId(), "customerId");
        String conversationId = requireIdentifier(request.conversationId(), "conversationId");
        String message = requireText(request.message(), "message", MAX_TEXT_LENGTH);
        return new ValidatedChat(customerId, conversationId, message);
    }

    private String requireIdentifier(String value, String field) {
        String identifier = requireText(value, field, 80);
        if (!identifier.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(field + " 只能包含字母、数字、下划线或连字符");
        }
        return identifier;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String scopedConversationId(String customerId, String conversationId) {
        return customerId + ":" + conversationId;
    }

    private void seedKnowledgeBase(VectorStore vectorStore) {
        vectorStore.add(List.of(
                document("faq-refund", "退款政策", "faq://refund",
                        "未发货订单可以申请取消。已发货订单需签收后七天内申请退货，商品应保持完好。退款原路返回，通常需要三到五个工作日。"),
                document("faq-shipping", "配送说明", "faq://shipping",
                        "普通订单付款后四十八小时内发货。发货后可查询物流单号；偏远地区和节假日可能延迟。"),
                document("faq-human", "人工客服", "faq://human",
                        "需要修改收货地址、处理支付争议或知识库无法确认的问题时，应转接人工客服进行身份核验。")));
    }

    private Document document(String idSeed, String title, String source, String text) {
        String id = UUID.nameUUIDFromBytes(idSeed.getBytes(StandardCharsets.UTF_8)).toString();
        return new Document(id, text, Map.of("title", title, "source", source, "category", "support"));
    }

    public record ChatRequest(String customerId, String conversationId, String message) {
    }

    public record ChatResult(String conversationId, String answer, List<Source> sources) {
    }

    public record Source(String title, String source, Double score) {
    }

    public record ClearResult(String conversationId, int deletedMessages) {
    }

    public record Status(int trackedConversationLocks, String chatModel) {
    }

    private record ValidatedChat(String customerId, String conversationId, String message) {
    }

}
