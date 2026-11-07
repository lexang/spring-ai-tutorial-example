package com.example.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 内容生成服务
 * <p>
 * 演示：
 * - 多模板管理
 * - 文章生成
 * - 大纲生成
 */
@Service
public class ContentGenerationService {

    private final ChatClient chatClient;
    private final Map<String, PromptTemplate> templates;

    public ContentGenerationService(ChatClient.Builder builder) {
        this.chatClient = builder.build();

        // 加载多个模板
        this.templates = Map.of(
                "article", loadTemplate("prompts/article.st"),
                "outline", loadTemplate("prompts/outline.st")
        );
    }

    /**
     * 生成文章
     */
    public String generateArticle(ArticleRequest request) {
        if (request == null) {
            throw new InvalidTemplateRequestException("文章请求不能为空");
        }
        String topic = TemplateRequestValidation.requireText(request.topic(), "topic");
        int length = TemplateRequestValidation.requireRange(request.length(), "length", 100, 5000);
        String style = TemplateRequestValidation.requireText(request.style(), "style");
        List<String> keywords = TemplateRequestValidation.optionalTextList(request.keywords(), "keywords");

        Prompt prompt = templates.get("article").create(Map.of(
                "docType", "技术",
                "topic", topic,
                "length", String.valueOf(length),
                "style", style,
                "keywords", keywords.isEmpty() ? "无指定关键词" : String.join(", ", keywords)
        ));

        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    /**
     * 生成大纲
     */
    public String generateOutline(String topic) {
        topic = TemplateRequestValidation.requireText(topic, "topic");
        Prompt prompt = templates.get("outline").create(Map.of(
                "topic", topic
        ));

        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    /**
     * 加载模板
     */
    private PromptTemplate loadTemplate(String path) {
        return new PromptTemplate(new ClassPathResource(path));
    }
}

/**
 * 文章生成请求
 */
record ArticleRequest(
        String topic,
        int length,
        String style,
        List<String> keywords
) {
}
