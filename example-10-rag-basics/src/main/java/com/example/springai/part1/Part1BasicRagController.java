package com.example.springai.part1;

import com.example.springai.support.RagInputValidator;
import com.example.springai.support.RagResponseValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础 RAG HTTP 接口。
 */
@RestController
@RequestMapping("/api/rag/part1")
public class Part1BasicRagController {

    private final SimpleRagService simpleRagService;
    private final FilteredRagService filteredRagService;
    private final ModularRagService modularRagService;
    private final ChatClient customRagChatClient;

    public Part1BasicRagController(
            SimpleRagService simpleRagService,
            FilteredRagService filteredRagService,
            ModularRagService modularRagService,
            @Qualifier("customRagChatClient") ChatClient customRagChatClient) {
        this.simpleRagService = simpleRagService;
        this.filteredRagService = filteredRagService;
        this.modularRagService = modularRagService;
        this.customRagChatClient = customRagChatClient;
    }

    @GetMapping("/simple")
    public String simpleRag(@RequestParam String question) {
        return this.simpleRagService.ask(question);
    }

    @GetMapping("/filtered")
    public String filteredRag(
            @RequestParam String question,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String category) {
        return this.filteredRagService.ask(question, product, category);
    }

    @GetMapping("/custom")
    public String customTemplateRag(@RequestParam String question) {
        String content = this.customRagChatClient.prompt()
                .user(RagInputValidator.requireQuestion(question))
                .call()
                .content();
        return RagResponseValidator.requireContent(content);
    }

    @GetMapping("/modular")
    public String modularRag(@RequestParam String question) {
        return this.modularRagService.ask(question);
    }

    @GetMapping("/modular/filtered")
    public String filteredModularRag(
            @RequestParam String question,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String category) {
        return this.modularRagService.askFiltered(question, product, category);
    }

    @GetMapping("/modular/allow-empty")
    public String modularRagAllowingEmptyContext(@RequestParam String question) {
        return this.modularRagService.askAllowingEmptyContext(question);
    }

    @GetMapping("/health")
    public String health() {
        return "Part 1 基础 RAG 接口正常";
    }
}
