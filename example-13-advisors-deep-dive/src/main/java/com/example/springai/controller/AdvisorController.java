package com.example.springai.controller;

import com.example.springai.service.AdvisorPipelineService;
import com.example.springai.service.AdvisorPipelineService.ChatReply;
import com.example.springai.service.AdvisorPipelineService.ModelStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/advisors")
public class AdvisorController {

    private final AdvisorPipelineService advisorPipelineService;

    public AdvisorController(AdvisorPipelineService advisorPipelineService) {
        this.advisorPipelineService = advisorPipelineService;
    }

    @GetMapping("/status")
    public ModelStatus status() {
        return this.advisorPipelineService.status();
    }

    @PostMapping("/call")
    public ChatReply call(@RequestBody ChatRequest request) {
        return this.advisorPipelineService.call(request == null ? null : request.message());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody ChatRequest request) {
        return this.advisorPipelineService.stream(request == null ? null : request.message());
    }

    @PostMapping("/preview")
    public AdvisorPipelineService.PreviewReply preview(@RequestBody ChatRequest request) {
        return this.advisorPipelineService.preview(request == null ? null : request.message());
    }

    public record ChatRequest(String message) {
    }

}
