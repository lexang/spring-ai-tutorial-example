package com.example.springai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 外部系统模板的完整示例：固定资源路径、显式 UTF-8、系统和用户消息分开。
 */
@Service
public final class ExpertAssistant {

    private final ChatClient chatClient;

    private final SystemPromptTemplate systemTemplate;

    public ExpertAssistant(ChatClient.Builder builder,
            @Value("classpath:/prompts/system-expert.st") Resource resource) {
        this.chatClient = builder.build();
        try {
            // 2.0.2 的 Resource 构造函数使用 JVM 默认字符集，这里显式选择 UTF-8。
            this.systemTemplate = new SystemPromptTemplate(resource.getContentAsString(StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new IllegalStateException("无法读取专家系统模板", exception);
        }
    }

    public String ask(String question, String expertise) {
        Prompt prompt = buildPrompt(question, expertise);
        return TemplateResponseSupport.requireContent(this.chatClient.prompt(prompt).call().content());
    }

    /** 只渲染和检查输入，不调用模型；帮助确认最终系统/用户消息。 */
    public PromptPreview preview(String question, String expertise) {
        Prompt prompt = buildPrompt(question, expertise);
        return new PromptPreview(prompt.getSystemMessage().getText(), prompt.getUserMessage().getText());
    }

    private Prompt buildPrompt(String question, String expertise) {
        String safeQuestion = TemplateRequestValidation.requireText(question, "question");
        String safeExpertise = TemplateRequestValidation.requireText(expertise, "expertise");
        Message system = this.systemTemplate.createMessage(Map.of(
                "expertise", safeExpertise, "language", "中文"));
        return new Prompt(List.of(system, new UserMessage(safeQuestion)));
    }

    public record PromptPreview(String system, String user) { }

}
