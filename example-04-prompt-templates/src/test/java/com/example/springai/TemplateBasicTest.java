package com.example.springai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 不依赖外部模型的 Prompt 模板契约测试。
 */
class TemplateBasicTest {

    @Test
    void shouldRenderBasicPromptTemplate() {
        PromptTemplate template = new PromptTemplate("给我讲一个关于{topic}的{style}故事");

        assertThat(template.render(Map.of("topic", "太空探险", "style", "科幻")))
                .isEqualTo("给我讲一个关于太空探险的科幻故事");
    }

    @Test
    void shouldCreatePromptWithOneUserMessage() {
        Prompt prompt = new PromptTemplate("介绍一下{topic}的历史")
                .create(Map.of("topic", "人工智能"));

        assertThat(prompt.getInstructions()).hasSize(1);
        assertThat(prompt.getInstructions().get(0)).isInstanceOf(UserMessage.class);
        assertThat(prompt.getInstructions().get(0).getText()).contains("人工智能");
    }

    @Test
    void shouldAttachBuiltChatOptionsToPrompt() {
        ChatOptions options = ChatOptions.builder()
                .temperature(0.4)
                .maxTokens(500)
                .build();

        Prompt prompt = new PromptTemplate("解释{topic}")
                .create(Map.of("topic", "Spring AI"), options);

        assertThat(prompt.getOptions()).isSameAs(options);
        assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.4);
        assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(500);
    }

    @Test
    void shouldComposeMessagesInOrder() {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("你是一个 Java 专家"),
                new UserMessage("什么是 Spring Boot")));

        assertThat(prompt.getInstructions().get(0).getMessageType().getValue()).isEqualTo("system");
        assertThat(prompt.getInstructions().get(1).getMessageType().getValue()).isEqualTo("user");
    }

    @Test
    void shouldExposePromptConvenienceMethods() {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("系统规则"),
                new UserMessage("第一个问题"),
                new UserMessage("第二个问题")));

        assertThat(prompt.getSystemMessage().getText()).isEqualTo("系统规则");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("第二个问题");
        assertThat(prompt.getSystemMessages()).hasSize(1);
        assertThat(prompt.getUserMessages()).hasSize(2);
    }

    @Test
    void shouldCreateSystemMessageWithSystemPromptTemplate() {
        Message message = new SystemPromptTemplate("你是一个{role}，擅长{domain}领域")
                .createMessage(Map.of("role", "架构师", "domain", "微服务"));

        assertThat(message).isInstanceOf(SystemMessage.class);
        assertThat(message.getText()).contains("架构师", "微服务");
    }

    @Test
    void shouldMapRecordPropertiesExplicitly() {
        record ArticleRequest(String topic, int length, String style) {
        }

        ArticleRequest request = new ArticleRequest("Spring AI", 500, "技术");
        PromptTemplate template = new PromptTemplate("写一篇关于{topic}的{length}字{style}文章");

        assertThat(template.render(Map.of(
                "topic", request.topic(),
                "length", request.length(),
                "style", request.style())))
                .isEqualTo("写一篇关于Spring AI的500字技术文章");
    }

    @Test
    void shouldRenderJsonWithCustomDelimiters() {
        PromptTemplate template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template("{\"name\": \"<name>\", \"age\": <age>}")
                .build();

        assertThat(template.render(Map.of("name", "张三", "age", 25)))
                .isEqualTo("{\"name\": \"张三\", \"age\": 25}");
    }

    @Test
    void shouldKeepStaticTemplateWithNoOpRenderer() {
        PromptTemplate template = PromptTemplate.builder()
                .renderer(new NoOpTemplateRenderer())
                .template("已经完成的 JSON：{\"ready\":true}")
                .build();

        assertThat(template.render(Map.of())).isEqualTo("已经完成的 JSON：{\"ready\":true}");
    }

    @Test
    void shouldRenderListWithStringTemplateSeparator() {
        PromptTemplate template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .validationMode(ValidationMode.NONE)
                        .build())
                .template("标签：{tags; separator=\", \"}")
                .build();

        assertThat(template.render(Map.of("tags", List.of("Java", "Spring", "AI"))))
                .isEqualTo("标签：Java, Spring, AI");
    }

    @Test
    void shouldUseCustomDollarRenderer() {
        PromptTemplate template = PromptTemplate.builder()
                .renderer(new DollarTemplateRenderer())
                .template("用户 ${name} 的年龄是 ${age}")
                .build();

        assertThat(template.render(Map.of("name", "张三", "age", 25)))
                .isEqualTo("用户 张三 的年龄是 25");
    }

    @Test
    void shouldRejectMissingVariablesByDefault() {
        PromptTemplate template = new PromptTemplate("你好，{name}");

        assertThatThrownBy(() -> template.render(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldJoinBatchTextsWithoutCallingModel() {
        List<String> texts = List.of("Hello", "Good morning", "Thank you");

        assertThat(String.join("\n---\n", texts))
                .isEqualTo("Hello\n---\nGood morning\n---\nThank you");
        assertThat(TemplateRequestValidation.optionalTextList(texts, "texts"))
                .containsExactlyElementsOf(texts);
    }

    @Test
    void shouldHandleOptionalKeywordsAndRejectBlankItems() {
        assertThat(TemplateRequestValidation.optionalTextList(null, "keywords")).isEmpty();
        assertThatThrownBy(() -> TemplateRequestValidation.optionalTextList(List.of("Java", " "), "keywords"))
                .isInstanceOf(InvalidTemplateRequestException.class)
                .hasMessageContaining("keywords第 2 项");
    }

    @Test
    void shouldFormatOrderHistoryInputSafely() {
        List<Order> orders = List.of(
                new Order("20240101", 299.0, "已发货"),
                new Order("20240102", 599.0, "配送中"));

        String history = orders.stream()
                .map(order -> String.format("订单号: %s, 金额: %.2f元, 状态: %s",
                        order.id(), order.amount(), order.status()))
                .reduce((first, second) -> first + "\n" + second)
                .orElse("暂无订单记录");

        assertThat(history).contains("订单号: 20240101", "金额: 299.00元", "状态: 已发货");
    }

    @Test
    void shouldRejectEmptyModelResponse() {
        assertThatThrownBy(() -> TemplateResponseSupport.requireContent((String) null))
                .isInstanceOf(EmptyTemplateResponseException.class);
        assertThatThrownBy(() -> TemplateResponseSupport.requireContent(new ChatResponse(List.of())))
                .isInstanceOf(EmptyTemplateResponseException.class);
    }

    @Test
    void shouldExposeUsageAsNullableMetadataInsteadOfThrowing() {
        Map<String, Object> usage = TemplateResponseSupport.usageSummary(new ChatResponse(List.of()));

        assertThat(usage).containsKeys("promptTokens", "completionTokens", "totalTokens",
                "cacheReadInputTokens", "cacheWriteInputTokens");
    }

}
