package com.example.springai.controller;

import com.example.springai.service.ChatConversationService;
import com.example.springai.service.ChatConversationService.ChatReply;
import com.example.springai.service.ChatConversationService.ModelStatus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MessageChatMemoryAdvisor 对话端点。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatConversationService chatConversationService;

    public ChatController(ChatConversationService chatConversationService) {
        this.chatConversationService = chatConversationService;
    }

    @GetMapping("/status")
    public ModelStatus status() {
        return this.chatConversationService.status();
    }

    @PostMapping("/{storage}/{conversationId}")
    public ChatReply chat(
            @PathVariable String storage,
            @PathVariable String conversationId,
            @RequestBody ChatRequest request) {
        return this.chatConversationService.chat(storage, conversationId, request.message());
    }

    public record ChatRequest(String message) {
    }
}
