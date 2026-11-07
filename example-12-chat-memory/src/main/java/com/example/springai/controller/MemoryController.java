package com.example.springai.controller;

import com.example.springai.service.MemoryManagementService;
import com.example.springai.service.MemoryManagementService.MemorySnapshot;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 完全离线可用的 ChatMemory 管理端点。
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryManagementService memoryManagementService;

    public MemoryController(MemoryManagementService memoryManagementService) {
        this.memoryManagementService = memoryManagementService;
    }

    @PostMapping("/{storage}/{conversationId}/messages")
    public MemorySnapshot addMessage(
            @PathVariable String storage,
            @PathVariable String conversationId,
            @RequestBody AddMessageRequest request) {
        return this.memoryManagementService.addMessage(
                storage, conversationId, request.role(), request.text());
    }

    @GetMapping("/{storage}/{conversationId}")
    public MemorySnapshot getMemory(
            @PathVariable String storage,
            @PathVariable String conversationId) {
        return this.memoryManagementService.snapshot(storage, conversationId);
    }

    @DeleteMapping("/{storage}/{conversationId}")
    public MemorySnapshot clearMemory(
            @PathVariable String storage,
            @PathVariable String conversationId) {
        return this.memoryManagementService.clear(storage, conversationId);
    }

    public record AddMessageRequest(String role, String text) {
    }
}
