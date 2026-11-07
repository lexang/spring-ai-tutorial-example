package com.example.springai.ragmodules.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DocumentJoinService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentJoinService.class);

    private final DocumentJoiner documentJoiner;

    public DocumentJoinService() {
        this.documentJoiner = new ConcatenationDocumentJoiner();
    }

    /**
     * 合并多个查询的检索结果
     */
    public List<Document> joinDocuments(Map<Query, List<List<Document>>> documentsForQuery) {
        List<Document> mergedDocuments = documentJoiner.join(documentsForQuery);

        logger.info("合并后得到 {} 个唯一文档", mergedDocuments.size());
        return mergedDocuments;
    }
}
