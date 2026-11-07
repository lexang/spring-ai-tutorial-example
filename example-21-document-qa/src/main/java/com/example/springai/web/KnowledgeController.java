package com.example.springai.web;

import com.example.springai.service.KnowledgeBaseService;
import com.example.springai.service.KnowledgeBaseService.AnswerResult;
import com.example.springai.service.KnowledgeBaseService.AskRequest;
import com.example.springai.service.KnowledgeBaseService.DeleteResult;
import com.example.springai.service.KnowledgeBaseService.IngestRequest;
import com.example.springai.service.KnowledgeBaseService.IngestResult;
import com.example.springai.service.KnowledgeBaseService.KnowledgeStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/status")
    public KnowledgeStatus status() {
        return this.knowledgeBaseService.status();
    }

    @PostMapping("/documents")
    public IngestResult ingest(@RequestBody IngestRequest request) {
        return this.knowledgeBaseService.ingest(request);
    }

    @PostMapping("/ask")
    public AnswerResult ask(@RequestBody AskRequest request) {
        return this.knowledgeBaseService.ask(request);
    }

    @DeleteMapping("/documents")
    public DeleteResult delete(@RequestParam String source) {
        return this.knowledgeBaseService.deleteBySource(source);
    }

}
