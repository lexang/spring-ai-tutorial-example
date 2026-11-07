package com.example.springai.reader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

/** 将受校验的 HTTP 文本请求适配为官方 ETL DocumentReader 契约。 */
public final class RequestDocumentReader implements DocumentReader {

    private final String title;

    private final String content;

    private final String source;

    private final String category;

    public RequestDocumentReader(String title, String content, String source, String category) {
        this.title = title;
        this.content = content;
        this.source = source;
        this.category = category;
    }

    @Override
    public List<Document> get() {
        String id = UUID.nameUUIDFromBytes(this.source.getBytes(StandardCharsets.UTF_8)).toString();
        return List.of(new Document(id, this.content, Map.of(
                "title", this.title,
                "source", this.source,
                "category", this.category,
                "language", "zh-CN")));
    }

}
