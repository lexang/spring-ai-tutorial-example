package com.example.springai.web;

import com.example.springai.model.KnowledgeModels.*;
import com.example.springai.service.PersonalKnowledgeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge")
public class PersonalKnowledgeController {
    private final PersonalKnowledgeService service;
    public PersonalKnowledgeController(PersonalKnowledgeService service) { this.service = service; }
    @GetMapping("/status") public KnowledgeStatus status() { return service.status(); }
    @PostMapping("/notes") public ResponseEntity<Note> create(@RequestBody CreateNoteRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request)); }
    @PutMapping("/notes/{id}") public Note update(@PathVariable String id, @RequestBody UpdateNoteRequest request) { return service.update(id, request); }
    @DeleteMapping("/notes/{id}") public DeleteResult delete(@PathVariable String id, @RequestParam String ownerId) { return service.delete(id, ownerId); }
    @PostMapping("/search") public SearchResult search(@RequestBody SearchRequest request) { return service.search(request); }
    @PostMapping("/ask") public AnswerResult ask(@RequestBody AskRequest request) { return service.ask(request); }
    @PostMapping("/reindex") public ReindexResult reindex() { return service.reindexAll(); }
}
