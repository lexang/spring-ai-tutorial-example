package com.example.springai;

import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vector Store REST API 控制器
 *
 * 提供文档管理和搜索的 HTTP 接口
 *
 * @author Spring AI Tutorials
 */
@RestController
@RequestMapping("/api")
public class VectorStoreController {

    private final DocumentService documentService;
    private final QAService qaService;

    private final VectorStore vectorStore;

    public VectorStoreController(DocumentService documentService, QAService qaService, VectorStore vectorStore) {
        this.documentService = documentService;
        this.qaService = qaService;
        this.vectorStore = vectorStore;
    }

    /**
     * 1. 添加单个文档
     *
     * POST /api/documents
     * Body: {"content": "文档内容", "metadata": {"category": "技术"}}
     */
    @PostMapping("/documents")
    public Map<String, Object> addDocument(@RequestBody DocumentService.DocumentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String documentId = documentService.addDocument(request.content(), request.metadata());

        return Map.of(
                "message", "文档添加成功",
                "documentId", documentId
        );
    }

    /**
     * 2. 批量添加文档
     *
     * POST /api/documents/batch
     * Body: [{"content": "文档1", "metadata": {}}, {"content": "文档2", "metadata": {}}]
     */
    @PostMapping("/documents/batch")
    public Map<String, Object> addDocuments(@RequestBody List<DocumentService.DocumentRequest> requests) {
        documentService.addDocuments(requests);

        return Map.of(
                "message", "批量添加成功",
                "count", requests.size()
        );
    }

    /**
     * 3. 简单搜索
     *
     * GET /api/documents/search?query=Spring&topK=5
     */
    @GetMapping("/documents/search")
    public Map<String, Object> search(
            @RequestParam String query,
            @RequestParam(required = false) Integer topK) {

        List<DocumentService.SearchResult> results = documentService.search(query, topK);

        return Map.of(
                "query", query,
                "results", results,
                "count", results.size()
        );
    }

    /** 查询放入 POST 正文，避免完整问题出现在 URL 中；这不等于认证或日志脱敏。 */
    @PostMapping("/documents/search")
    public Map<String, Object> searchStructured(@RequestBody SearchInput input) {
        if (input == null) { throw new IllegalArgumentException("搜索请求不能为空"); }
        var results = documentService.searchWithDSL(input.query(), input.category(), input.year(),
                input.topK(), input.threshold());
        return Map.of("results", results, "count", results.size());
    }

    public record SearchInput(String query, Integer topK, Double threshold, String category, Integer year) { }

    /**
     * 4. 带过滤的搜索
     *
     * GET /api/documents/search/filter?query=Java&filter=category=='编程语言'&topK=5
     */
    @GetMapping("/documents/search/filter")
    public Map<String, Object> searchWithFilter(
            @RequestParam String query,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) Double threshold) {

        List<DocumentService.SearchResult> results = documentService.searchWithFilter(
                query, filter, topK, threshold
        );

        return Map.of(
                "query", query,
                "filter", filter != null ? filter : "无",
                "results", results,
                "count", results.size()
        );
    }

    /**
     * 5. 使用 DSL 搜索
     *
     * GET /api/documents/search/dsl?query=AI&category=人工智能
     */
    @GetMapping("/documents/search/dsl")
    public Map<String, Object> searchWithDSL(
            @RequestParam String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer topK) {

        List<DocumentService.SearchResult> results = documentService.searchWithDSL(
                query, category, year, topK
        );

        return Map.of(
                "query", query,
                "category", category != null ? category : "全部",
                "year", year != null ? year : "不限",
                "results", results,
                "count", results.size()
        );
    }

    /**
     * 6. 删除文档(通过 ID)
     *
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/documents/{id}")
    public Map<String, Object> deleteDocument(@PathVariable String id) {
        documentService.deleteById(id);

        return Map.of(
                "message", "文档删除成功",
                "documentId", id
        );
    }

    /**
     * 7. 删除文档(字符串删除过滤器表达式)
     *
     * DELETE /api/documents/filter?expression=category=='测试'
     */
    @DeleteMapping("/documents/filter")
    public Map<String, Object> deleteByFilter(@RequestParam String expression) {
        documentService.deleteByFilter(expression);

        return Map.of(
                "message", "符合条件的文档已删除",
                "filter", expression
        );
    }

    /**
     * 8. 问答接口
     *
     * GET /api/qa/ask?question=什么是Spring AI
     */
    @GetMapping("/qa/ask")
    public QAService.QAResponse askQuestion(@RequestParam String question) {
        return qaService.askQuestion(question);
    }

    /**
     * 9. 带类别过滤的问答
     *
     * GET /api/qa/ask/category?question=如何使用&category=Spring AI
     */
    @GetMapping("/qa/ask/category")
    public QAService.QAResponse askQuestionWithCategory(
            @RequestParam String question,
            @RequestParam(required = false) String category) {

        return qaService.askQuestionWithCategory(question, category);
    }

    /**
     * 10. 获取统计信息
     *
     * GET /api/documents/stats
     */
    @GetMapping("/documents/stats")
    public Map<String, Object> getStats() {
        boolean simpleStore = this.vectorStore instanceof SimpleVectorStore;
        return Map.of(
                "vectorStore", this.vectorStore.getName(),
                "type", simpleStore ? "内存演示实现" : "外部向量存储",
                "automaticPersistence", !simpleStore,
                "message", simpleStore
                        ? "SimpleVectorStore 不提供计数 API，可用 save/load 显式管理 JSON 快照"
                        : "持久化、事务和统计能力取决于具体后端"
        );
    }
}
