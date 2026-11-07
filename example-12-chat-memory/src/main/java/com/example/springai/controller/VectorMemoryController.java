package com.example.springai.controller;

import com.example.springai.service.VectorMemoryChatService;
import com.example.springai.service.VectorMemoryChatService.ClearVectorMemoryReply;
import com.example.springai.service.VectorMemoryChatService.VectorReply;
import com.example.springai.service.VectorMemoryChatService.VectorStatus;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * VectorStoreChatMemoryAdvisor 对话端点。
 */
@RestController
@RequestMapping("/api/vector-memory")
public class VectorMemoryController {

    private final VectorMemoryChatService vectorMemoryChatService;

    public VectorMemoryController(VectorMemoryChatService vectorMemoryChatService) {
        this.vectorMemoryChatService = vectorMemoryChatService;
    }

    @GetMapping("/status")
    public VectorStatus status() {
        return this.vectorMemoryChatService.status();
    }

    @PostMapping("/{conversationId}")
    public VectorReply chat(
            @PathVariable String conversationId,
            @RequestBody VectorChatRequest request) {
        return this.vectorMemoryChatService.chat(
                conversationId, request.message(), request.topK());
    }

    @DeleteMapping("/{conversationId}")
    public ClearVectorMemoryReply clear(@PathVariable String conversationId) {
        return this.vectorMemoryChatService.clear(conversationId);
    }

    public record VectorChatRequest(String message, Integer topK) {
    }
}
