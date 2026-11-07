package com.example.springai.ragmodules.postretrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

/**
 * 保留检索结果前 N 个文档，控制最终提示词的上下文规模。
 */
public final class TopDocumentPostProcessor implements DocumentPostProcessor {

    private final int maxDocuments;

    public TopDocumentPostProcessor(int maxDocuments) {
        if (maxDocuments <= 0) {
            throw new IllegalArgumentException("最大文档数必须大于 0");
        }
        this.maxDocuments = maxDocuments;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (query == null) {
            throw new IllegalArgumentException("查询不能为 null");
        }
        if (documents == null) {
            throw new IllegalArgumentException("文档列表不能为 null");
        }
        return documents.stream()
                .limit(this.maxDocuments)
                .toList();
    }
}
