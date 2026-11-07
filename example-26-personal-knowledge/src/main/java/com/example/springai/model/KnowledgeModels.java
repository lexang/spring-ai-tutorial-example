package com.example.springai.model;

import java.time.Instant;
import java.util.List;

public final class KnowledgeModels {

    private KnowledgeModels() {
    }

    public record Note(String id, String ownerId, String title, String content, String tag,
            int version, Instant createdAt, Instant updatedAt) {
    }

    public record CreateNoteRequest(String ownerId, String title, String content, String tag) {
    }

    public record UpdateNoteRequest(String ownerId, String title, String content, String tag,
            Integer expectedVersion) {
    }

    public record SearchRequest(String ownerId, String query, String tag, Integer topK) {
    }

    public record SearchHit(String id, String title, String tag, int version, Double score,
            String excerpt) {
    }

    public record SearchResult(String query, List<SearchHit> hits) {
    }

    public record AskRequest(String ownerId, String question, String tag, Integer topK) {
    }

    public record Source(String id, String title, String tag, int version, Double score,
            String excerpt) {
    }

    public record AnswerResult(String question, String answer, List<Source> sources, String status) {
    }

    public record DeleteResult(String id, int deletedRows) {
    }

    public record ReindexResult(int noteCount, int indexedCount, String vectorStore) {
    }

    public record KnowledgeStatus(long noteCount, int indexedCount, String vectorStore,
            String chatModel) {
    }

}
