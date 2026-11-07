package com.example.springai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板示例端点。
 *
 * <p>端点只负责组装 Prompt；输入校验、模型响应防护和异常转换分别由辅助类处理。</p>
 */
@RestController
@RequestMapping("/template")
public class TemplateController {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final TranslationService translationService;
    private final ContentGenerationService contentService;
    private final CustomerServiceBot customerServiceBot;
    private final ExpertAssistant expertAssistant;

    @Value("classpath:/prompts/translate.st")
    private Resource translateTemplate;

    @Value("classpath:/prompts/code-review.st")
    private Resource codeReviewTemplate;

    @Value("classpath:/prompts/system-role.st")
    private Resource systemRoleTemplate;

    public TemplateController(
            ChatClient.Builder builder,
            ChatModel chatModel,
            TranslationService translationService,
            ContentGenerationService contentService,
            CustomerServiceBot customerServiceBot,
            ExpertAssistant expertAssistant) {
        this.chatClient = builder.build();
        this.chatModel = chatModel;
        this.translationService = translationService;
        this.contentService = contentService;
        this.customerServiceBot = customerServiceBot;
        this.expertAssistant = expertAssistant;
    }

    /**
     * 使用默认的 {} 分隔符创建 PromptTemplate。
     */
    @GetMapping("/basic")
    public String basic(@RequestParam String topic, @RequestParam String style) {
        topic = TemplateRequestValidation.requireText(topic, "topic");
        style = TemplateRequestValidation.requireText(style, "style");

        PromptTemplate promptTemplate = new PromptTemplate("给我讲一个关于{topic}的{style}故事");
        Prompt prompt = promptTemplate.create(Map.of("topic", topic, "style", style));
        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    /**
     * 从 Resource 加载模板。这里故意保留与 TranslationService 不同的写法，便于对比资源模板 API。
     */
    @PostMapping("/translate")
    public String translate(@RequestBody TranslateRequest request) {
        if (request == null) {
            throw new InvalidTemplateRequestException("翻译请求不能为空");
        }
        String text = TemplateRequestValidation.requireText(request.text(), "text");
        String source = TemplateRequestValidation.requireText(request.source(), "source");
        String target = TemplateRequestValidation.requireText(request.target(), "target");

        Prompt prompt = new PromptTemplate(translateTemplate).create(Map.of(
                "text", text,
                "sourceLanguage", source,
                "targetLanguage", target));
        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    @PostMapping("/code-review")
    public String codeReview(@RequestBody CodeReviewRequest request) {
        if (request == null) {
            throw new InvalidTemplateRequestException("代码审查请求不能为空");
        }
        String code = TemplateRequestValidation.requireText(request.code(), "code");
        String language = TemplateRequestValidation.requireText(request.language(), "language");

        Prompt prompt = new PromptTemplate(codeReviewTemplate).create(Map.of(
                "code", code,
                "language", language));
        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    @GetMapping("/fluent")
    public String fluent(@RequestParam String name, @RequestParam String skill) {
        String safeName = TemplateRequestValidation.requireText(name, "name");
        String safeSkill = TemplateRequestValidation.requireText(skill, "skill");
        return TemplateResponseSupport.requireContent(chatClient.prompt()
                .user(user -> user.text("你好{name}，你擅长{skill}")
                        .param("name", safeName)
                        .param("skill", safeSkill))
                .call()
                .content());
    }

    @GetMapping("/multi-message")
    public String multiMessage(@RequestParam String role, @RequestParam String question) {
        role = TemplateRequestValidation.requireText(role, "role");
        question = TemplateRequestValidation.requireText(question, "question");

        Prompt prompt = new Prompt(List.of(
                new SystemMessage("你是一个" + role),
                new UserMessage(question)));
        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    @GetMapping("/system-template")
    public String systemTemplate(@RequestParam String role,
            @RequestParam String domain,
            @RequestParam String question) {
        role = TemplateRequestValidation.requireText(role, "role");
        domain = TemplateRequestValidation.requireText(domain, "domain");
        question = TemplateRequestValidation.requireText(question, "question");

        SystemPromptTemplate template = new SystemPromptTemplate(systemRoleTemplate);
        Message systemMessage = template.createMessage(Map.of("role", role, "domain", domain));
        Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(question)));
        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    @GetMapping("/custom-delimiter")
    public String customDelimiter(@RequestParam String name, @RequestParam int age) {
        name = TemplateRequestValidation.requireText(name, "name");
        age = TemplateRequestValidation.requireRange(age, "age", 0, 150);

        PromptTemplate template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template("""
                        请生成一个 JSON 对象：
                        {
                            "name": "<name>",
                            "age": <age>,
                            "skills": ["<skill1>", "<skill2>"],
                            "description": "这是一个用户信息对象"
                        }

                        只返回 JSON，不要额外说明。
                        """)
                .build();

        Prompt prompt = template.create(Map.of(
                "name", name,
                "age", age,
                "skill1", "Java",
                "skill2", "Python"));
        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    @GetMapping("/expert")
    public String expert(@RequestParam String question, @RequestParam String expertise) {
        return this.expertAssistant.ask(question, expertise);
    }

    @PostMapping("/preview")
    public ExpertAssistant.PromptPreview preview(@RequestBody ExpertRequest request) {
        if (request == null) throw new InvalidTemplateRequestException("请求体不能为空");
        return this.expertAssistant.preview(request.question(), request.expertise());
    }

    @PostMapping("/expert")
    public String expert(@RequestBody ExpertRequest request) {
        if (request == null) throw new InvalidTemplateRequestException("请求体不能为空");
        return this.expertAssistant.ask(request.question(), request.expertise());
    }

    @GetMapping("/render")
    public Map<String, String> renderExample(@RequestParam String topic) {
        topic = TemplateRequestValidation.requireText(topic, "topic");
        PromptTemplate template = PromptTemplate.builder()
                .template("介绍一下{topic}的历史")
                .build();

        String rendered = template.render(Map.of("topic", topic));
        String aiResponse = TemplateResponseSupport.requireContent(chatClient.prompt(
                template.create(Map.of("topic", topic))).call().content());

        return Map.of("rendered", rendered, "aiResponse", aiResponse);
    }

    @GetMapping("/token-usage")
    public Map<String, Object> tokenUsage(@RequestParam String question) {
        question = TemplateRequestValidation.requireText(question, "question");
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(question)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("answer", TemplateResponseSupport.requireContent(response));
        result.put("usage", TemplateResponseSupport.usageSummary(response));
        return result;
    }

    @PostMapping("/batch-translate")
    public String batchTranslate(@RequestBody BatchTranslateRequest request) {
        if (request == null) {
            throw new InvalidTemplateRequestException("批量翻译请求不能为空");
        }
        return translationService.batchTranslate(request.texts(), request.source(), request.target());
    }

    @PostMapping("/generate-article")
    public String generateArticle(@RequestBody ArticleRequest request) {
        return contentService.generateArticle(request);
    }

    @GetMapping("/generate-outline")
    public String generateOutline(@RequestParam String topic) {
        return contentService.generateOutline(topic);
    }

    @PostMapping("/customer-service")
    public String customerService(@RequestBody CustomerQuery query) {
        return customerServiceBot.handleQuery(query);
    }
}

record TranslateRequest(String text, String source, String target) {
}

record ExpertRequest(String question, String expertise) { }

record CodeReviewRequest(String code, String language) {
}

record BatchTranslateRequest(List<String> texts, String source, String target) {
}
