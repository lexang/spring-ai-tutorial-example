package com.example.springai.rag;

import com.example.springai.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库工具 - 演示 ReturnDirect 特性
 *
 * returnDirect = true 表示工具结果直接返回给用户,不经过模型处理
 * 适用于 RAG 场景,文档内容可以直接展示
 */
@Component
public class KnowledgeBaseTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseTools.class);

    // 模拟向量数据库
    private final Map<String, List<Document>> knowledgeBase = new ConcurrentHashMap<>();

    public KnowledgeBaseTools() {
        // 初始化示例文档
        knowledgeBase.put("spring", Arrays.asList(
                new Document("doc1", "Spring Framework 是一个开源的 Java 应用框架,提供了全面的基础设施支持。"),
                new Document("doc2", "Spring Boot 简化了 Spring 应用的开发,提供自动配置和快速启动能力。"),
                new Document("doc3", "Spring AI 是 Spring 生态的 AI 应用开发框架,支持多种 AI 模型。")
        ));

        knowledgeBase.put("java", Arrays.asList(
                new Document("doc4", "Java 是一种面向对象的编程语言,具有平台无关性。"),
                new Document("doc5", "Java 17 是最新的 LTS 版本,提供了许多新特性。")
        ));

        knowledgeBase.put("ai", Arrays.asList(
                new Document("doc6", "人工智能 (AI) 是计算机科学的一个分支,致力于创建智能机器。"),
                new Document("doc7", "大语言模型 (LLM) 是 AI 的重要应用,如 GPT、Claude 等。")
        ));

        log.info("KnowledgeBaseTools 初始化完成,已加载 {} 个主题的文档", knowledgeBase.size());
    }

    /**
     * 搜索知识库 - 直接返回结果
     *
     * returnDirect = true 使工具结果直接返回给用户
     * 跳过模型的处理,适合文档检索场景
     *
     * returnDirect = false (默认) 使工具结果发送给模型处理
     *
     * @param query 查询关键词
     * @return 匹配的文档内容
     */
    @Tool(
            description = "从知识库中检索相关文档,返回匹配的文档内容",
            returnDirect = true  // 直接返回,不经过模型处理
    )
    public String searchKnowledgeBase(
            @ToolParam(description = "查询关键词,例如: Spring, Java, AI")
            String query) {

        log.info("工具被调用: searchKnowledgeBase (returnDirect=true), query={}", query);

        // 简单的关键词匹配
        List<Document> matchedDocs = new ArrayList<>();

        for (Map.Entry<String, List<Document>> entry : knowledgeBase.entrySet()) {
            if (query.toLowerCase().contains(entry.getKey().toLowerCase())) {
                matchedDocs.addAll(entry.getValue());
            }
        }

        // 如果没有直接匹配,尝试内容搜索
        if (matchedDocs.isEmpty()) {
            for (List<Document> docs : knowledgeBase.values()) {
                for (Document doc : docs) {
                    if (doc.content().toLowerCase().contains(query.toLowerCase())) {
                        matchedDocs.add(doc);
                    }
                }
            }
        }

        if (matchedDocs.isEmpty()) {
            log.info("未找到匹配的文档: query={}", query);
            return "抱歉,未找到与 \"" + query + "\" 相关的文档。";
        }

        // 格式化返回结果
        StringBuilder result = new StringBuilder();
        result.append("📚 找到 ").append(matchedDocs.size())
                .append(" 个相关文档:\n\n");

        for (int i = 0; i < matchedDocs.size(); i++) {
            Document doc = matchedDocs.get(i);
            result.append(String.format("【文档 %d - %s】\n%s\n\n",
                    i + 1, doc.id(), doc.content()));
        }

        log.info("检索成功: query={}, 找到 {} 个文档", query, matchedDocs.size());
        return result.toString();
    }


    /**
     * 添加文档到知识库
     *
     * @param topic 主题
     * @param content 文档内容
     * @return 添加结果
     */
    @Tool(description = "向知识库添加新文档")
    public String addDocument(
            @ToolParam(description = "文档主题") String topic,
            @ToolParam(description = "文档内容") String content) {

        log.info("工具被调用: addDocument, topic={}", topic);

        String docId = "doc" + System.currentTimeMillis();
        Document newDoc = new Document(docId, content);

        knowledgeBase.computeIfAbsent(topic.toLowerCase(), k -> new ArrayList<>())
                .add(newDoc);

        String result = String.format("✅ 文档已添加到知识库\n主题: %s\nID: %s",
                topic, docId);

        log.info("添加文档成功: topic={}, docId={}", topic, docId);
        return result;
    }
}
