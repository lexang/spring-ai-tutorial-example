package com.example.springai.support;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 校验模型元数据增强器的结果。
 *
 * <p>Spring AI 内置增强器对空模型响应的处理并不完全相同：关键词增强器可能不写入
 * 元数据，而摘要增强器会写入空字符串。HTTP 示例不能把这种结果当作成功索引，
 * 因此在应用边界统一转换成安全的业务异常。</p>
 */
public final class DocumentEnrichmentValidator {

    private DocumentEnrichmentValidator() {
    }

    public static List<Document> requireKeywords(List<Document> documents) {
        requireNonBlankMetadata(documents, "excerpt_keywords", "关键词");
        return documents;
    }

    public static List<Document> requireCurrentSummaries(List<Document> documents) {
        requireNonBlankMetadata(documents, "section_summary", "摘要");
        return documents;
    }

    private static void requireNonBlankMetadata(List<Document> documents, String key, String label) {
        if (documents == null || documents.isEmpty()) {
            throw new EtlOperationException(label + "增强没有可处理的文档。");
        }
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            Object value = document.getMetadata().get(key);
            if (!(value instanceof String text) || text.isBlank()) {
                throw new EtlOperationException(
                        label + "增强未返回有效内容（第 " + (index + 1) + " 个文档）。请检查 ChatModel 响应。");
            }
        }
    }

}
