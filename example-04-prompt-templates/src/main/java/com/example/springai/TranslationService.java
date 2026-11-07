package com.example.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 翻译服务
 * <p>
 * 演示：
 * - 使用外部模板文件
 * - 单次翻译
 * - 批量翻译
 */
@Service
public class TranslationService {

    private final ChatClient chatClient;
    private final PromptTemplate translateTemplate;

    public TranslationService(
            ChatClient.Builder builder,
            @Value("classpath:/prompts/translate.st") Resource template) {

        this.chatClient = builder
                .defaultSystem("你是专业的翻译专家")
                .build();

        this.translateTemplate = new PromptTemplate(template);
    }

    /**
     * 单次翻译
     */
    public String translate(String text, String source, String target) {
        text = TemplateRequestValidation.requireText(text, "text");
        source = TemplateRequestValidation.requireText(source, "source");
        target = TemplateRequestValidation.requireText(target, "target");
        Prompt prompt = translateTemplate.create(Map.of(
                "text", text,
                "sourceLanguage", source,
                "targetLanguage", target
        ));

        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    /**
     * 批量翻译
     */
    public String batchTranslate(List<String> texts, String source, String target) {
        List<String> safeTexts = TemplateRequestValidation.optionalTextList(texts, "texts");
        if (safeTexts.isEmpty()) {
            throw new InvalidTemplateRequestException("texts 至少包含一项");
        }
        source = TemplateRequestValidation.requireText(source, "source");
        target = TemplateRequestValidation.requireText(target, "target");
        String batchText = String.join("\n---\n", safeTexts);

        Prompt prompt = translateTemplate.create(Map.of(
                "text", batchText,
                "sourceLanguage", source,
                "targetLanguage", target
        ));

        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }
}
