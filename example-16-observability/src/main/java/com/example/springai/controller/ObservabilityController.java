package com.example.springai.controller;

import java.util.Map;

import reactor.core.publisher.Flux;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springai.service.ObservabilityDemoService;
import com.example.springai.service.ObservabilityDemoService.ChatReply;

/** 可观测性演示 REST 接口。 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final ObservabilityDemoService demoService;

    public ObservabilityController(ObservabilityDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("chatModel", "offline-demo", "vectorStore", "simple", "externalCredentialsRequired", false,
            "actuatorMetrics", "/actuator/metrics", "prometheus", "/actuator/prometheus");
    }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody MessageRequest request) {
        return this.demoService.chat(requireMessage(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody MessageRequest request) {
        return this.demoService.stream(requireMessage(request));
    }

    @PostMapping("/vector/search")
    public Object search(@RequestBody SearchRequestPayload request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        int topK = request.topK() == null ? 3 : request.topK();
        if (topK < 1 || topK > 10) {
            throw new IllegalArgumentException("topK 必须在 1 到 10 之间");
        }
        return this.demoService.search(request.query(), topK);
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return this.demoService.metrics();
    }

    private String requireMessage(MessageRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        return request.message();
    }

    public record MessageRequest(String message) {
    }

    public record SearchRequestPayload(String query, Integer topK) {
    }

}
