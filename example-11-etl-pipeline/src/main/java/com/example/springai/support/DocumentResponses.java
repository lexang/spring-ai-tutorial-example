package com.example.springai.support;

import org.springframework.ai.document.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Spring AI Document 转成适合教程接口展示的响应结构。
 */
public final class DocumentResponses {

    private DocumentResponses() {
    }

    public static Map<String, Object> result(List<Document> documents, int previewLength) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentCount", documents.size());
        result.put("documents", views(documents, previewLength));
        return result;
    }

    public static List<Map<String, Object>> views(List<Document> documents, int previewLength) {
        return documents.stream()
                .map(document -> view(document, previewLength))
                .toList();
    }

    public static Map<String, Object> view(Document document, int previewLength) {
        String text = document.getText() == null ? "" : document.getText();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", document.getId());
        view.put("content", preview(text, previewLength));
        view.put("contentLength", text.length());
        view.put("metadata", document.getMetadata());
        return view;
    }

    public static String preview(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }
}
