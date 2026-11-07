package com.example.springai;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话控制器
 */
@RestController
@RequestMapping("/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 发送消息
     */
    @PostMapping("/{conversationId}")
    public Map<String, String> chat(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> request) {

        if (request == null) {
            throw new InvalidDemoRequestException("请求体不能为空");
        }
        String message = request.get("message");
        String response = conversationService.chat(conversationId, message);

        return Map.of(
                "conversationId", conversationId,
                "response", response
        );
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/{conversationId}/history")
    public List<Map<String, String>> getHistory(@PathVariable String conversationId) {
        return conversationService.getHistory(conversationId);
    }

    /**
     * 重置对话
     */
    @DeleteMapping("/{conversationId}")
    public Map<String, String> reset(@PathVariable String conversationId) {
        conversationService.reset(conversationId);
        return Map.of("message", "对话已重置");
    }

    /**
     * 获取所有对话
     */
    @GetMapping("/list")
    public List<String> listConversations() {
        return conversationService.getConversationIds();
    }
}
