package com.example.springai.service;

import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

/** 演示服务：调用 ChatClient、流式 ChatClient 和 VectorStore。 */
@Service
public class ObservabilityDemoService {

    private final ChatClient chatClient;

    private final SimpleVectorStore vectorStore;

    private final MeterRegistry meterRegistry;

    public ObservabilityDemoService(ChatClient chatClient, SimpleVectorStore vectorStore,
            MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.meterRegistry = meterRegistry;
    }

    public ChatReply chat(String message) {
        ChatResponse response = this.chatClient.prompt(message).call().chatResponse();
        return ChatReply.from(response);
    }

    public Flux<String> stream(String message) {
        return this.chatClient.prompt(message).stream().content();
    }

    public List<VectorHit> search(String query, int topK) {
        return this.vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build())
            .stream()
            .map(document -> new VectorHit(document.getId(), document.getText(), document.getScore()))
            .toList();
    }

    public Map<String, Object> metrics() {
        List<String> names = this.meterRegistry.getMeters().stream()
            .map(Meter::getId)
            .map(id -> id.getName() + " " + id.getTags())
            .sorted()
            .toList();
        return Map.of("meterCount", names.size(), "meters", names);
    }

    public record ChatReply(String content, String model, Integer promptTokens, Integer completionTokens,
            Integer totalTokens) {

        static ChatReply from(ChatResponse response) {
            if (response == null || response.getResult() == null) {
                throw new IllegalStateException("模型未返回有效响应");
            }
            var usage = response.getMetadata().getUsage();
            if (usage == null || usage instanceof org.springframework.ai.chat.metadata.EmptyUsage) {
                return new ChatReply(response.getResult().getOutput().getText(), response.getMetadata().getModel(),
                        null, null, null);
            }
            return new ChatReply(response.getResult().getOutput().getText(), response.getMetadata().getModel(),
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }

    }

    public record VectorHit(String id, String text, Double score) {
    }

}
